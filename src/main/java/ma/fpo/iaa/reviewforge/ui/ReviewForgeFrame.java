package ma.fpo.iaa.reviewforge.ui;

import ma.fpo.iaa.reviewforge.domain.Evidence;
import ma.fpo.iaa.reviewforge.domain.Paper;
import ma.fpo.iaa.reviewforge.domain.Rebuttal;
import ma.fpo.iaa.reviewforge.domain.Review;
import ma.fpo.iaa.reviewforge.service.PdfPaperExtractor;

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.plaf.basic.BasicTabbedPaneUI;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class ReviewForgeFrame extends JFrame {
    private static final Color BG = new Color(13, 18, 32);
    private static final Color PANEL = new Color(24, 31, 49);
    private static final Color PANEL_2 = new Color(31, 40, 62);
    private static final Color BORDER = new Color(56, 68, 96);
    private static final Color TEXT = new Color(235, 240, 250);
    private static final Color MUTED = new Color(155, 169, 196);
    private static final Color PURPLE = new Color(124, 92, 255);
    private static final Color CYAN = new Color(41, 205, 190);
    private static final Color ORANGE = new Color(255, 176, 74);
    private static final Color RED = new Color(255, 100, 116);

    private final Consumer<Paper> onAnalyze;
    private final JTextField title = field();
    private final JTextArea abstractText = area(5);
    private final JSpinner datasetSize = new JSpinner(new SpinnerNumberModel(180, 0, 1000000, 10));
    private final JSpinner accuracy = new JSpinner(new SpinnerNumberModel(91.0, 0.0, 100.0, 0.1));
    private final JCheckBox baseline = check("Baseline comparative", true);
    private final JCheckBox stats = check("Tests statistiques", false);
    private final JCheckBox limitations = check("Limites discutées", false);
    private final JCheckBox ethics = check("Déclaration éthique", true);
    private final JCheckBox anonymized = check("Données anonymisées", true);
    private final JButton analyzeButton = primaryButton("▶  LANCER L'ANALYSE MULTI-AGENT");
    private final JLabel platformStatus = new JLabel("●  Initialisation de JADE...");
    private final JTabbedPane tabs = new JTabbedPane();
    private final DefaultListModel<String> timelineModel = new DefaultListModel<>();
    private final JList<String> timeline = new JList<>(timelineModel);
    private final List<AgentCard> agentCards = new ArrayList<>();
    private final JPanel reportsGrid = panel(new GridLayout(1, 3, 12, 12), BG);
    private final JPanel agentGrid = panel(new GridLayout(1, 5, 10, 10), BG);
    private final JLabel decisionLabel = new JLabel("EN ATTENTE", SwingConstants.CENTER);
    private final JLabel scoreLabel = new JLabel("— / 10", SwingConstants.CENTER);
    private final JLabel explanation = new JLabel("Lancez une analyse pour produire une décision traçable.", SwingConstants.CENTER);
    private final ScoreChart scoreChart = new ScoreChart();
    private final JTextArea rebuttalArea = area(7);
    private final JButton exportButton = secondaryButton("⇩  Exporter le rapport");
    private final JLabel pdfStatus = new JLabel("Aucun PDF chargé - mode démonstration manuelle");
    private final JTextArea evidenceArea = area(20);
    private List<Review> initialReviews = List.of();
    private List<Review> finalReviews = List.of();
    private Rebuttal rebuttal;
    private ReviewForgeEvents.Decision decision;
    private Paper currentPaper;
    private Paper extractedPaper;
    private boolean running;

    public ReviewForgeFrame(Consumer<Paper> onAnalyze) {
        super("ReviewForge — Scientific Peer Review Multi-Agent System");
        this.onAnalyze = onAnalyze;
        installTheme();
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(900, 680));
        setSize(1380, 860);
        setLocationRelativeTo(null);
        setContentPane(buildRoot());
        styleTabs();
        loadStrongExample();
        ReviewForgeEvents.subscribe(event -> SwingUtilities.invokeLater(() -> handle(event)));
        analyzeButton.addActionListener(e -> startAnalysis());
        exportButton.addActionListener(e -> exportReport());
        addComponentListener(new ComponentAdapter() {
            public void componentResized(ComponentEvent e) { adaptLayout(); }
        });
    }

    private JPanel buildRoot() {
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(BG);
        root.add(buildHeader(), BorderLayout.NORTH);
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, buildInputPanel(), buildWorkspace());
        split.setDividerLocation(350);
        split.setDividerSize(1);
        split.setResizeWeight(.27);
        split.setContinuousLayout(true);
        split.setBorder(null);
        split.setBackground(BORDER);
        root.add(split, BorderLayout.CENTER);
        return root;
    }

    private JComponent buildHeader() {
        JPanel header = panel(new BorderLayout(), BG);
        header.setBorder(new CompoundBorder(new MatteBorder(0, 0, 1, 0, BORDER), new EmptyBorder(18, 24, 18, 24)));
        JPanel brand = panel(new FlowLayout(FlowLayout.LEFT, 12, 0), BG);
        JLabel logo = new JLabel("RF");
        logo.setOpaque(true); logo.setBackground(PURPLE); logo.setForeground(Color.WHITE);
        logo.setFont(new Font("SansSerif", Font.BOLD, 18)); logo.setHorizontalAlignment(SwingConstants.CENTER);
        logo.setPreferredSize(new Dimension(44, 44));
        brand.add(logo);
        JPanel names = panel(new GridLayout(2, 1), BG);
        JLabel name = new JLabel("ReviewForge"); name.setForeground(TEXT); name.setFont(new Font("SansSerif", Font.BOLD, 22));
        JLabel subtitle = new JLabel("COMITÉ SCIENTIFIQUE MULTI-AGENT • JAVA + JADE");
        subtitle.setForeground(MUTED); subtitle.setFont(new Font("SansSerif", Font.BOLD, 10));
        names.add(name); names.add(subtitle); brand.add(names);
        platformStatus.setForeground(ORANGE); platformStatus.setFont(new Font("SansSerif", Font.BOLD, 12));
        header.add(brand, BorderLayout.WEST); header.add(platformStatus, BorderLayout.EAST);
        return header;
    }

    private JComponent buildInputPanel() {
        JPanel outer = panel(new BorderLayout(), PANEL);
        outer.setBorder(new EmptyBorder(18, 20, 18, 20));
        JPanel form = panel();
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.add(sectionTitle("SOUMISSION SCIENTIFIQUE"));
        form.add(gap(14));
        JButton pdfButton = primaryButton("PDF  CHARGER UN ARTICLE");
        pdfButton.addActionListener(e -> loadPdf());
        form.add(pdfButton); form.add(gap(7));
        pdfStatus.setForeground(MUTED); pdfStatus.setFont(new Font("SansSerif", Font.PLAIN, 10));
        form.add(pdfStatus); form.add(gap(14));
        form.add(subLabel("OU TESTER SANS PDF")); form.add(gap(7));
        JPanel examples = panel(new GridLayout(2, 1, 0, 7), PANEL);
        JButton strongExample = secondaryButton("✓ Exemple solide");
        JButton weakExample = secondaryButton("⚠ Exemple faible");
        strongExample.addActionListener(e -> loadStrongExample());
        weakExample.addActionListener(e -> loadWeakExample());
        examples.add(strongExample); examples.add(weakExample); examples.setMaximumSize(new Dimension(Integer.MAX_VALUE, 91));
        form.add(examples); form.add(gap(12));
        form.add(label("Titre de l'article")); form.add(title); form.add(gap(10));
        form.add(label("Résumé"));
        JScrollPane abstractScroll = scroll(abstractText); abstractScroll.setPreferredSize(new Dimension(300, 110));
        form.add(abstractScroll); form.add(gap(10));
        form.add(label("Taille du dataset")); styleSpinner(datasetSize); form.add(datasetSize); form.add(gap(10));
        form.add(label("Accuracy annoncée (%)")); styleSpinner(accuracy); form.add(accuracy); form.add(gap(12));
        JLabel warning = new JLabel("<html>Mode PDF: valeurs détectées automatiquement.<br>Mode exemple: valeurs manuelles de démonstration.</html>");
        warning.setForeground(CYAN); warning.setFont(new Font("SansSerif", Font.PLAIN, 10));
        form.add(warning); form.add(gap(12));
        form.add(sectionTitle("CRITÈRES D'ÉVALUATION")); form.add(gap(8));
        for (JCheckBox box : List.of(baseline, stats, limitations, ethics, anonymized)) form.add(box);
        form.add(gap(14));
        form.add(analyzeButton);
        JScrollPane pane = scroll(form); pane.setBorder(null); pane.getVerticalScrollBar().setUnitIncrement(16);
        pane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        outer.add(pane, BorderLayout.CENTER);
        return outer;
    }

    private JComponent buildWorkspace() {
        JPanel workspace = panel(new BorderLayout(), BG);
        tabs.setFont(new Font("SansSerif", Font.BOLD, 12));
        tabs.addTab("  FLUX EN DIRECT  ", buildLiveTab());
        tabs.addTab("  PREUVES EXTRAITES DU PDF  ", buildEvidenceTab());
        tabs.addTab("  RAPPORTS DES REVIEWERS  ", buildReportsTab());
        tabs.addTab("  DÉCISION & EXPORT  ", buildDecisionTab());
        workspace.add(tabs, BorderLayout.CENTER);
        return workspace;
    }

    private JComponent buildLiveTab() {
        JPanel live = panel(new BorderLayout(0, 14), BG); live.setBorder(new EmptyBorder(18, 18, 18, 18));
        agentCards.add(new AgentCard("CHAIR", "Coordination", PURPLE));
        agentCards.add(new AgentCard("MÉTHODOLOGIE", "Protocole", CYAN));
        agentCards.add(new AgentCard("RÉSULTATS", "Reproductibilité", ORANGE));
        agentCards.add(new AgentCard("ÉTHIQUE", "Biais & privacy", RED));
        agentCards.add(new AgentCard("AUTEUR", "Rebuttal", new Color(91, 149, 255)));
        agentCards.forEach(agentGrid::add); live.add(agentGrid, BorderLayout.NORTH);
        JPanel center = cardPanel(new BorderLayout());
        center.add(sectionTitle("JOURNAL DES COMMUNICATIONS ACL"), BorderLayout.NORTH);
        timeline.setBackground(PANEL); timeline.setForeground(TEXT); timeline.setSelectionBackground(PANEL_2);
        timeline.setFont(new Font("Monospaced", Font.PLAIN, 13)); timeline.setFixedCellHeight(30);
        center.add(scroll(timeline), BorderLayout.CENTER); live.add(center, BorderLayout.CENTER);
        return live;
    }

    private JComponent buildEvidenceTab() {
        JPanel page = panel(new BorderLayout(0, 12), BG);
        page.setBorder(new EmptyBorder(18, 18, 18, 18));
        JPanel header = panel(new BorderLayout(), BG);
        header.add(sectionTitle("DONNÉES ET PREUVES EXTRAITES AUTOMATIQUEMENT"), BorderLayout.WEST);
        JLabel notice = new JLabel("Chaque détection cite la page et la phrase source");
        notice.setForeground(MUTED); header.add(notice, BorderLayout.EAST);
        page.add(header, BorderLayout.NORTH);
        evidenceArea.setEditable(false); evidenceArea.setFont(new Font("Monospaced", Font.PLAIN, 13));
        evidenceArea.setText("Chargez un article PDF pour afficher les preuves détectées.");
        page.add(scroll(evidenceArea), BorderLayout.CENTER);
        return page;
    }

    private JComponent buildReportsTab() {
        JPanel page = panel(new BorderLayout(0, 14), BG); page.setBorder(new EmptyBorder(18, 18, 18, 18));
        JPanel top = panel(new BorderLayout(), BG);
        top.add(sectionTitle("ÉVALUATIONS SPÉCIALISÉES APRÈS CONSENSUS"), BorderLayout.WEST);
        JLabel hint = new JLabel("Score • confiance • forces • réserves"); hint.setForeground(MUTED); top.add(hint, BorderLayout.EAST);
        page.add(top, BorderLayout.NORTH);
        showEmptyReports();
        JScrollPane reportsScroll = scroll(reportsGrid);
        reportsScroll.setBorder(null);
        reportsScroll.getVerticalScrollBar().setUnitIncrement(16);
        reportsScroll.getHorizontalScrollBar().setUnitIncrement(16);
        page.add(reportsScroll, BorderLayout.CENTER);
        JPanel rebuttalCard = cardPanel(new BorderLayout());
        rebuttalCard.setPreferredSize(new Dimension(100, 165));
        rebuttalCard.add(sectionTitle("RÉPONSE DE L'AUTEUR / REBUTTAL"), BorderLayout.NORTH);
        rebuttalArea.setEditable(false); rebuttalArea.setForeground(MUTED);
        rebuttalCard.add(scroll(rebuttalArea), BorderLayout.CENTER); page.add(rebuttalCard, BorderLayout.SOUTH);
        return page;
    }

    private JComponent buildDecisionTab() {
        JPanel page = panel(new BorderLayout(16, 16), BG); page.setBorder(new EmptyBorder(22, 22, 22, 22));
        JPanel hero = cardPanel(new BorderLayout(10, 8)); hero.setBorder(new CompoundBorder(new LineBorder(PURPLE, 1), new EmptyBorder(24, 24, 24, 24)));
        JLabel small = new JLabel("DÉCISION EXPLICABLE DU CHAIR AGENT", SwingConstants.CENTER); small.setForeground(MUTED); small.setFont(new Font("SansSerif", Font.BOLD, 11));
        decisionLabel.setForeground(TEXT); decisionLabel.setFont(new Font("SansSerif", Font.BOLD, 34));
        scoreLabel.setForeground(CYAN); scoreLabel.setFont(new Font("SansSerif", Font.BOLD, 24));
        explanation.setForeground(MUTED);
        JPanel heroText = panel(); heroText.setLayout(new BoxLayout(heroText, BoxLayout.Y_AXIS));
        for (JComponent c : List.of(small, decisionLabel, scoreLabel, explanation)) { c.setAlignmentX(Component.CENTER_ALIGNMENT); heroText.add(c); heroText.add(gap(7)); }
        hero.add(heroText, BorderLayout.CENTER); page.add(hero, BorderLayout.NORTH);
        JPanel middle = cardPanel(new BorderLayout()); middle.add(sectionTitle("COMPARAISON DES SCORES"), BorderLayout.NORTH); middle.add(scoreChart, BorderLayout.CENTER);
        page.add(middle, BorderLayout.CENTER);
        JPanel bottom = panel(new FlowLayout(FlowLayout.RIGHT), BG); exportButton.setEnabled(false); bottom.add(exportButton); page.add(bottom, BorderLayout.SOUTH);
        return page;
    }

    private void startAnalysis() {
        if (running) return;
        if (title.getText().isBlank() || abstractText.getText().isBlank()) {
            JOptionPane.showMessageDialog(this, "Veuillez saisir un titre et un résumé.", "Soumission incomplète", JOptionPane.WARNING_MESSAGE); return;
        }
        currentPaper = extractedPaper != null ? extractedPaper : new Paper(
                title.getText().trim(), abstractText.getText().trim(), (Integer) datasetSize.getValue(),
                baseline.isSelected(), stats.isSelected(), limitations.isSelected(), ethics.isSelected(),
                anonymized.isSelected(), ((Number) accuracy.getValue()).doubleValue() / 100.0);
        resetResults(); running = true; analyzeButton.setEnabled(false); analyzeButton.setText("ANALYSE EN COURS...");
        agentCards.get(0).setState("COORDINATION", PURPLE); addTimeline("SUBMIT", "Utilisateur → Chair Agent : nouvelle soumission");
        onAnalyze.accept(currentPaper); tabs.setSelectedIndex(0);
    }

    @SuppressWarnings("unchecked")
    private void handle(ReviewForgeEvents.Event event) {
        switch (event.type()) {
            case PLATFORM_READY -> { platformStatus.setText("●  " + event.message()); platformStatus.setForeground(CYAN); analyzeButton.setEnabled(true); agentCards.forEach(c -> c.setState("PRÊT", CYAN)); }
            case PHASE -> { addTimeline("PHASE", event.message()); advanceAgents(event.message()); }
            case LOG -> addTimeline("ACL", event.message());
            case INITIAL_REVIEWS -> { initialReviews = (List<Review>) event.data(); addTimeline("INFORM", "3 reviewers → Chair : rapports initiaux reçus"); agentCards.subList(1, 4).forEach(c -> c.setState("RAPPORT ENVOYÉ", ORANGE)); }
            case REBUTTAL -> { rebuttal = (Rebuttal) event.data(); showRebuttal(); addTimeline("INFORM", "Author → Chair : rebuttal et engagements"); agentCards.get(4).setState("RÉPONSE ENVOYÉE", new Color(91,149,255)); }
            case FINAL_REVIEWS -> { finalReviews = (List<Review>) event.data(); renderReports(finalReviews); scoreChart.setReviews(initialReviews, finalReviews); addTimeline("INFORM", "Reviewers → Chair : scores révisés"); }
            case DECISION -> finish((ReviewForgeEvents.Decision) event.data());
            case ERROR -> { addTimeline("ERREUR", event.message()); running = false; analyzeButton.setEnabled(true); analyzeButton.setText("▶  RELANCER L'ANALYSE"); JOptionPane.showMessageDialog(this, event.message(), "ReviewForge", JOptionPane.ERROR_MESSAGE); }
        }
    }

    private void finish(ReviewForgeEvents.Decision value) {
        decision = value; running = false; analyzeButton.setEnabled(true); analyzeButton.setText("↻  NOUVELLE ANALYSE");
        decisionLabel.setText(value.label()); scoreLabel.setText(String.format("%.2f / 10", value.score()));
        decisionLabel.setForeground(value.score() >= 6 ? CYAN : value.score() >= 4.5 ? ORANGE : RED);
        explanation.setText("Consensus pondéré par la confiance • 3 expertises • 2 tours • décision traçable");
        exportButton.setEnabled(true); agentCards.get(0).setState("DÉCISION PUBLIÉE", CYAN);
        addTimeline("DECISION", String.format("Chair → Comité : %s (%.2f/10)", value.label(), value.score()));
        tabs.setSelectedIndex(3);
    }

    private void advanceAgents(String phase) {
        if (phase.contains("reçu")) agentCards.subList(1, 4).forEach(c -> c.setState("ANALYSE", ORANGE));
        if (phase.contains("rebuttal")) agentCards.get(4).setState("RÉPONSE", PURPLE);
        if (phase.contains("Consensus")) agentCards.subList(1, 4).forEach(c -> c.setState("RECONSIDÉRATION", PURPLE));
    }

    private void renderReports(List<Review> reviews) {
        reportsGrid.removeAll();
        reviews.stream().sorted((a,b) -> a.specialty().compareTo(b.specialty())).forEach(r -> reportsGrid.add(reviewCard(r)));
        reportsGrid.revalidate(); reportsGrid.repaint();
    }

    private JComponent reviewCard(Review r) {
        JPanel card = cardPanel(); card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        JLabel specialty = new JLabel(r.specialty().toUpperCase()); specialty.setForeground(PURPLE); specialty.setFont(new Font("SansSerif", Font.BOLD, 11));
        JLabel score = new JLabel(String.format("%.1f", r.score())); score.setForeground(TEXT); score.setFont(new Font("SansSerif", Font.BOLD, 38));
        JLabel meta = new JLabel("/ 10    confiance " + Math.round(r.confidence()*100) + "%"); meta.setForeground(MUTED);
        JLabel recommendation = pill(r.recommendation().replace('_', ' '), r.score() >= 6 ? CYAN : ORANGE);
        card.add(specialty); card.add(gap(10)); card.add(score); card.add(meta); card.add(gap(10)); card.add(recommendation); card.add(gap(14));
        card.add(subLabel("FORCES")); for (String s : r.strengths()) card.add(wrapped("✓  " + s, CYAN));
        card.add(gap(10)); card.add(subLabel("POINTS À VÉRIFIER")); for (String s : r.concerns()) card.add(wrapped("⚠  " + s, ORANGE));
        return card;
    }

    private void showEmptyReports() {
        reportsGrid.removeAll();
        for (String s : List.of("Méthodologie", "Résultats", "Éthique & biais")) {
            JPanel empty = cardPanel(new GridBagLayout()); JLabel l = new JLabel(s + " — en attente"); l.setForeground(MUTED); empty.add(l); reportsGrid.add(empty);
        }
    }

    private void showRebuttal() {
        StringBuilder text = new StringBuilder();
        rebuttal.responses().forEach(r -> text.append("• ").append(r).append("\n"));
        text.append("\nENGAGEMENT : ").append(rebuttal.revisionCommitment()); rebuttalArea.setText(text.toString());
    }

    private void resetResults() {
        initialReviews = List.of(); finalReviews = List.of(); rebuttal = null; decision = null;
        timelineModel.clear(); showEmptyReports(); rebuttalArea.setText("");
        decisionLabel.setText("EN COURS"); scoreLabel.setText("— / 10"); explanation.setText("Les agents analysent la soumission...");
        scoreChart.setReviews(List.of(), List.of()); exportButton.setEnabled(false);
        agentCards.forEach(c -> c.setState("PRÊT", CYAN));
    }

    private void exportReport() {
        if (decision == null) return;
        JFileChooser chooser = new JFileChooser(); chooser.setSelectedFile(new File("ReviewForge-Rapport.md"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        try {
            Files.writeString(chooser.getSelectedFile().toPath(), reportMarkdown(), StandardCharsets.UTF_8);
            JOptionPane.showMessageDialog(this, "Rapport exporté avec succès.", "ReviewForge", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception e) { JOptionPane.showMessageDialog(this, e.getMessage(), "Export impossible", JOptionPane.ERROR_MESSAGE); }
    }

    private String reportMarkdown() {
        StringBuilder b = new StringBuilder("# Rapport ReviewForge\n\n");
        b.append("## Article\n\n**").append(currentPaper.title()).append("**\n\n").append(currentPaper.abstractText()).append("\n\n");
        b.append("- Source : ").append(currentPaper.sourceFile()).append("\n- Pages : ").append(currentPaper.pageCount()).append("\n\n");
        b.append("## Preuves extraites\n\n");
        for (Evidence e : currentPaper.evidence()) b.append("- **").append(e.criterion()).append("** - ").append(e.display()).append("\n");
        b.append("\n");
        b.append("## Décision\n\n- Décision : **").append(decision.label()).append("**\n- Score pondéré : **").append(String.format("%.2f/10", decision.score())).append("**\n\n");
        b.append("## Rapports spécialisés\n\n");
        for (Review r : finalReviews) {
            b.append("### ").append(r.specialty()).append("\n\nScore : ").append(r.score()).append("/10 — Confiance : ").append(Math.round(r.confidence()*100)).append("%\n\n");
            b.append("Réserves :\n"); r.concerns().forEach(c -> b.append("- ").append(c).append("\n")); b.append("\n");
        }
        b.append("## Rebuttal\n\n").append(rebuttal.revisionCommitment()).append("\n\n");
        b.append("---\nGénéré par ReviewForge — système multi-agent Java/JADE.\n"); return b.toString();
    }

    private void loadPdf() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("Articles scientifiques PDF", "pdf"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        File file = chooser.getSelectedFile();
        pdfStatus.setText("Extraction en cours: " + file.getName()); pdfStatus.setForeground(ORANGE);
        analyzeButton.setEnabled(false);
        new SwingWorker<PdfPaperExtractor.Extraction, Void>() {
            protected PdfPaperExtractor.Extraction doInBackground() throws Exception {
                return new PdfPaperExtractor().extract(file);
            }
            protected void done() {
                try {
                    PdfPaperExtractor.Extraction extraction = get();
                    applyExtraction(extraction);
                    pdfStatus.setText("PDF analysé: " + file.getName() + " - " + extractedPaper.pageCount() + " pages");
                    pdfStatus.setForeground(CYAN); analyzeButton.setEnabled(true); tabs.setSelectedIndex(1);
                    if (!extraction.warnings().isEmpty()) JOptionPane.showMessageDialog(ReviewForgeFrame.this,
                            String.join("\n", extraction.warnings()), "Extraction terminée avec avertissements", JOptionPane.WARNING_MESSAGE);
                } catch (Exception ex) {
                    extractedPaper = null; pdfStatus.setText("Échec de l'extraction PDF"); pdfStatus.setForeground(RED);
                    analyzeButton.setEnabled(true); JOptionPane.showMessageDialog(ReviewForgeFrame.this,
                            ex.getCause() == null ? ex.getMessage() : ex.getCause().getMessage(), "PDF non analysable", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private void applyExtraction(PdfPaperExtractor.Extraction extraction) {
        extractedPaper = extraction.paper(); Paper p = extractedPaper;
        title.setText(p.title()); abstractText.setText(p.abstractText()); datasetSize.setValue(p.datasetSize());
        accuracy.setValue(Math.round(p.reportedAccuracy()*1000.0)/10.0); baseline.setSelected(p.hasBaseline());
        stats.setSelected(p.hasStatisticalTest()); limitations.setSelected(p.discussesLimitations());
        ethics.setSelected(p.hasEthicsStatement()); anonymized.setSelected(p.anonymizedData());
        StringBuilder out = new StringBuilder();
        out.append("SOURCE : ").append(p.sourceFile()).append("   |   PAGES : ").append(p.pageCount()).append("\n");
        out.append("TITRE  : ").append(p.title()).append("\n\n");
        out.append(String.format("DATASET  : %s%n", p.datasetSize() == 0 ? "NON DÉTECTÉ" : p.datasetSize()));
        out.append(String.format("ACCURACY : %s%n%n", p.reportedAccuracy() == 0 ? "NON DÉTECTÉE" : String.format("%.1f%%", p.reportedAccuracy()*100)));
        if (p.evidence().isEmpty()) out.append("Aucune preuve structurée détectée.\n");
        for (Evidence e : p.evidence()) {
            out.append("[").append(e.criterion()).append("]  ").append(e.display())
                    .append("  (confiance ").append(Math.round(e.confidence()*100)).append("%)\n\n");
        }
        if (!extraction.warnings().isEmpty()) {
            out.append("AVERTISSEMENTS\n"); extraction.warnings().forEach(w -> out.append("- ").append(w).append("\n"));
        }
        evidenceArea.setText(out.toString()); evidenceArea.setCaretPosition(0);
    }

    private void loadStrongExample() {
        extractedPaper = null; pdfStatus.setText("Mode exemple solide - aucune extraction PDF"); pdfStatus.setForeground(MUTED);
        Paper p = Paper.demoPaper(); title.setText(p.title()); abstractText.setText(p.abstractText());
        datasetSize.setValue(p.datasetSize()); accuracy.setValue(Math.round(p.reportedAccuracy()*1000.0)/10.0);
        baseline.setSelected(p.hasBaseline()); stats.setSelected(p.hasStatisticalTest()); limitations.setSelected(p.discussesLimitations()); ethics.setSelected(p.hasEthicsStatement()); anonymized.setSelected(p.anonymizedData());
    }

    private void loadWeakExample() {
        extractedPaper = null; pdfStatus.setText("Mode exemple faible - aucune extraction PDF"); pdfStatus.setForeground(MUTED);
        title.setText("Prédiction de la réussite scolaire avec un réseau de neurones");
        abstractText.setText("Cette étude utilise un réseau de neurones pour prédire la réussite scolaire. " +
                "L'expérimentation est réalisée sur 80 étudiants et obtient une accuracy de 72 %. " +
                "Le modèle n'est pas comparé à une méthode de référence. L'article ne fournit pas de tests " +
                "statistiques, ne discute pas les limites et ne précise pas si les données ont été anonymisées.");
        datasetSize.setValue(80); accuracy.setValue(72.0);
        baseline.setSelected(false); stats.setSelected(false); limitations.setSelected(false);
        ethics.setSelected(false); anonymized.setSelected(false);
    }

    private void styleTabs() {
        tabs.setUI(new BasicTabbedPaneUI());
        tabs.setBackground(BG); tabs.setForeground(MUTED);
        for (int i = 0; i < tabs.getTabCount(); i++) {
            tabs.setBackgroundAt(i, PANEL_2);
            tabs.setForegroundAt(i, TEXT);
        }
    }

    private void addTimeline(String type, String message) {
        timelineModel.addElement(LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")) + "   [" + type + "]   " + message);
        timeline.ensureIndexIsVisible(timelineModel.size()-1);
    }

    private void adaptLayout() {
        int width = getContentPane().getWidth();
        if (width < 1080) {
            agentGrid.setLayout(new GridLayout(0, 2, 10, 10));
            reportsGrid.setLayout(new GridLayout(0, 1, 12, 12));
            reportsGrid.setPreferredSize(new Dimension(520, 980));
        } else if (width < 1280) {
            agentGrid.setLayout(new GridLayout(0, 3, 10, 10));
            reportsGrid.setLayout(new GridLayout(1, 3, 12, 12));
            reportsGrid.setPreferredSize(null);
        } else {
            agentGrid.setLayout(new GridLayout(1, 5, 10, 10));
            reportsGrid.setLayout(new GridLayout(1, 3, 12, 12));
            reportsGrid.setPreferredSize(null);
        }
        agentGrid.revalidate(); reportsGrid.revalidate();
    }

    private static void installTheme() {
        UIManager.put("Panel.background", BG); UIManager.put("Label.foreground", TEXT);
        UIManager.put("TabbedPane.background", BG); UIManager.put("TabbedPane.foreground", MUTED);
        UIManager.put("TabbedPane.selected", PANEL); UIManager.put("TabbedPane.contentBorderInsets", new Insets(0,0,0,0));
        UIManager.put("TabbedPane.selectedForeground", TEXT);
        UIManager.put("TabbedPane.focusColor", PANEL);
        UIManager.put("Button.background", PANEL_2); UIManager.put("Button.foreground", TEXT);
        UIManager.put("Button.select", PURPLE); UIManager.put("Button.disabledText", MUTED);
        UIManager.put("CheckBox.background", PANEL); UIManager.put("CheckBox.foreground", TEXT);
        UIManager.put("TextField.background", PANEL_2); UIManager.put("TextField.foreground", TEXT);
        UIManager.put("TextField.caretForeground", TEXT); UIManager.put("TextField.inactiveForeground", MUTED);
        UIManager.put("TextArea.background", PANEL_2); UIManager.put("TextArea.foreground", TEXT);
        UIManager.put("TextArea.caretForeground", TEXT); UIManager.put("TextArea.inactiveForeground", MUTED);
        UIManager.put("FormattedTextField.background", PANEL_2); UIManager.put("FormattedTextField.foreground", TEXT);
        UIManager.put("FormattedTextField.caretForeground", TEXT);
        UIManager.put("Spinner.background", PANEL_2); UIManager.put("Spinner.foreground", TEXT);
        UIManager.put("List.background", PANEL); UIManager.put("List.foreground", TEXT);
        UIManager.put("List.selectionBackground", PANEL_2); UIManager.put("List.selectionForeground", TEXT);
        UIManager.put("ScrollPane.background", PANEL); UIManager.put("Viewport.background", PANEL);
        UIManager.put("ToolTip.background", PANEL_2); UIManager.put("ToolTip.foreground", TEXT);
        UIManager.put("OptionPane.messageForeground", TEXT);
        UIManager.put("ScrollPane.border", new LineBorder(BORDER)); UIManager.put("OptionPane.background", PANEL);
    }

    private static JPanel panel() { return panel(new FlowLayout(), PANEL); }
    private static JPanel panel(LayoutManager layout, Color bg) { JPanel p = new JPanel(layout); p.setBackground(bg); return p; }
    private static JPanel cardPanel() { return cardPanel(new FlowLayout(FlowLayout.LEFT)); }
    private static JPanel cardPanel(LayoutManager layout) { JPanel p = panel(layout, PANEL); p.setBorder(new CompoundBorder(new LineBorder(BORDER), new EmptyBorder(16,16,16,16))); return p; }
    private static JLabel label(String text) { JLabel l = new JLabel(text); l.setForeground(MUTED); l.setFont(new Font("SansSerif", Font.BOLD, 11)); l.setAlignmentX(Component.LEFT_ALIGNMENT); return l; }
    private static JLabel sectionTitle(String text) { JLabel l = new JLabel(text); l.setForeground(TEXT); l.setFont(new Font("SansSerif", Font.BOLD, 12)); return l; }
    private static JLabel subLabel(String text) { JLabel l = new JLabel(text); l.setForeground(MUTED); l.setFont(new Font("SansSerif", Font.BOLD, 10)); return l; }
    private static JTextField field() { JTextField f = new JTextField(); styleInput(f); f.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38)); return f; }
    private static JTextArea area(int rows) { JTextArea a = new JTextArea(rows, 10); a.setLineWrap(true); a.setWrapStyleWord(true); styleInput(a); return a; }
    private static void styleInput(JComponent c) { c.setBackground(PANEL_2); c.setForeground(TEXT); c.setFont(new Font("SansSerif", Font.PLAIN, 13)); c.setBorder(new CompoundBorder(new LineBorder(BORDER), new EmptyBorder(8,9,8,9))); }
    private static void styleSpinner(JSpinner s) {
        s.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        s.setOpaque(true); s.setBackground(PANEL_2); s.setForeground(TEXT);
        String format = s.getModel() instanceof SpinnerNumberModel model && model.getStepSize() instanceof Double
                ? "#0.0" : "#0";
        JSpinner.NumberEditor editor = new JSpinner.NumberEditor(s, format);
        s.setEditor(editor);
        JFormattedTextField text = editor.getTextField();
        text.setOpaque(true); text.setBackground(PANEL_2); text.setForeground(TEXT);
        text.setCaretColor(TEXT); text.setSelectionColor(PURPLE); text.setSelectedTextColor(Color.WHITE);
        text.setFont(new Font("SansSerif", Font.BOLD, 13));
        text.setHorizontalAlignment(SwingConstants.LEFT);
        text.setBorder(new CompoundBorder(new LineBorder(BORDER), new EmptyBorder(8, 9, 8, 9)));
    }
    private static JCheckBox check(String text, boolean selected) { JCheckBox c = new JCheckBox(text, selected); c.setForeground(TEXT); c.setBackground(PANEL); c.setFocusPainted(false); c.setFont(new Font("SansSerif", Font.PLAIN, 12)); c.setAlignmentX(Component.LEFT_ALIGNMENT); return c; }
    private static JButton primaryButton(String text) { JButton b = button(text, PURPLE); b.setForeground(Color.WHITE); return b; }
    private static JButton secondaryButton(String text) { return button(text, PANEL_2); }
    private static JButton button(String text, Color bg) { JButton b = new JButton(text); b.setOpaque(true); b.setContentAreaFilled(true); b.setBackground(bg); b.setForeground(TEXT); b.setFocusPainted(false); b.setFont(new Font("SansSerif", Font.BOLD, 11)); b.setBorder(new CompoundBorder(new LineBorder(BORDER), new EmptyBorder(10,14,10,14))); b.setAlignmentX(Component.LEFT_ALIGNMENT); b.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42)); return b; }
    private static JScrollPane scroll(Component c) { JScrollPane s = new JScrollPane(c); s.setBackground(PANEL); s.getViewport().setBackground(PANEL); s.setBorder(new LineBorder(BORDER)); return s; }
    private static Component gap(int px) { return Box.createRigidArea(new Dimension(1, px)); }
    private static JLabel pill(String text, Color color) { JLabel l = new JLabel("  " + text + "  "); l.setOpaque(true); l.setBackground(new Color(color.getRed(),color.getGreen(),color.getBlue(),55)); l.setForeground(color); l.setBorder(new EmptyBorder(5,6,5,6)); l.setFont(new Font("SansSerif", Font.BOLD, 10)); return l; }
    private static JLabel wrapped(String text, Color color) { JLabel l = new JLabel("<html><body style='width:210px'>" + text + "</body></html>"); l.setForeground(color); l.setBorder(new EmptyBorder(4,0,4,0)); return l; }

    private static class AgentCard extends JPanel {
        private final JLabel state = new JLabel("HORS LIGNE");
        AgentCard(String name, String role, Color accent) {
            super(); setLayout(new BoxLayout(this, BoxLayout.Y_AXIS)); setBackground(PANEL); setBorder(new CompoundBorder(new MatteBorder(3,0,0,0,accent), new EmptyBorder(13,13,13,13)));
            JLabel n = new JLabel(name); n.setForeground(TEXT); n.setFont(new Font("SansSerif",Font.BOLD,11));
            JLabel r = new JLabel(role); r.setForeground(MUTED); r.setFont(new Font("SansSerif",Font.PLAIN,10));
            state.setForeground(MUTED); state.setFont(new Font("SansSerif",Font.BOLD,9)); add(n); add(gap(5)); add(r); add(gap(12)); add(state);
        }
        void setState(String text, Color color) { state.setText("●  " + text); state.setForeground(color); }
    }

    private static class ScoreChart extends JPanel {
        private List<Review> initial = List.of(), finals = List.of();
        ScoreChart() { setBackground(PANEL); setPreferredSize(new Dimension(500,280)); }
        void setReviews(List<Review> a, List<Review> b) { initial=a; finals=b; repaint(); }
        protected void paintComponent(Graphics g) {
            super.paintComponent(g); Graphics2D x=(Graphics2D)g.create(); x.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            int w=getWidth(), h=getHeight(), base=h-42, top=35; x.setColor(BORDER); x.drawLine(55,base,w-25,base);
            if(finals.isEmpty()){x.setColor(MUTED);x.drawString("Les scores apparaîtront après le consensus.",55,h/2);x.dispose();return;}
            int group=(w-110)/finals.size();
            for(int i=0;i<finals.size();i++){Review f=finals.get(i); Review in=initial.stream().filter(r->r.specialty().equals(f.specialty())).findFirst().orElse(f); int x0=70+i*group; int maxH=base-top; int hi=(int)(in.score()/10*maxH), hf=(int)(f.score()/10*maxH);
                x.setColor(new Color(96,108,137));x.fillRoundRect(x0,base-hi,28,hi,8,8);x.setColor(PURPLE);x.fillRoundRect(x0+36,base-hf,28,hf,8,8);x.setColor(TEXT);x.drawString(String.format("%.1f",f.score()),x0+36,base-hf-7);x.setColor(MUTED);String n=f.specialty().startsWith("Éthique") ? "Éthique" : f.specialty().split(" ")[0];x.drawString(n,x0,base+22);}
            x.setColor(new Color(96,108,137));x.fillRect(w-210,12,12,12);x.setColor(MUTED);x.drawString("Initial",w-192,22);x.setColor(PURPLE);x.fillRect(w-120,12,12,12);x.setColor(MUTED);x.drawString("Consensus",w-102,22);x.dispose();
        }
    }
}
