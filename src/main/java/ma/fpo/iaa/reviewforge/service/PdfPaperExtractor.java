package ma.fpo.iaa.reviewforge.service;

import ma.fpo.iaa.reviewforge.domain.Evidence;
import ma.fpo.iaa.reviewforge.domain.Paper;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PdfPaperExtractor {
    public record Extraction(Paper paper, String fullText, List<String> warnings) {}
    private record PageText(int number, String text) {}

    private static final Pattern DATASET = Pattern.compile(
            "(?i)\\b(?:n\\s*[=:]\\s*)?(\\d{2,7})\\s+(participants?|students?|patients?|subjects?|" +
                    "samples?|images?|records?|observations?|instances?|étudiants?|patients?|sujets?|images?|échantillons?)\\b");
    private static final Pattern ACCURACY_AFTER = Pattern.compile(
            "(?i)\\b(?:accuracy|exactitude|précision globale|classification accuracy)\\b[^%\\n]{0,80}?(\\d{1,3}(?:[.,]\\d+)?)\\s*%");
    private static final Pattern ACCURACY_BEFORE = Pattern.compile(
            "(?i)\\b(\\d{1,3}(?:[.,]\\d+)?)\\s*%[^.\\n]{0,50}\\b(?:accuracy|exactitude|précision globale)\\b");

    public Extraction extract(File file) throws Exception {
        if (file == null || !file.getName().toLowerCase(Locale.ROOT).endsWith(".pdf"))
            throw new IllegalArgumentException("Sélectionnez un fichier PDF scientifique.");
        List<PageText> pages = new ArrayList<>();
        String metadataTitle;
        try (PDDocument document = Loader.loadPDF(file)) {
            metadataTitle = document.getDocumentInformation().getTitle();
            PDFTextStripper stripper = new PDFTextStripper();
            for (int page = 1; page <= document.getNumberOfPages(); page++) {
                stripper.setStartPage(page); stripper.setEndPage(page);
                pages.add(new PageText(page, clean(stripper.getText(document))));
            }
        }
        String fullText = pages.stream().map(PageText::text).reduce("", (a,b) -> a + "\n" + b).trim();
        if (fullText.length() < 100) throw new IllegalArgumentException(
                "Le PDF ne contient pas assez de texte. Il est peut-être scanné et nécessite un OCR.");

        List<Evidence> evidence = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        String title = validTitle(metadataTitle) ? metadataTitle.trim() : detectTitle(pages.get(0).text());
        String abstractText = detectAbstract(fullText);

        int datasetSize = detectNumber(pages, DATASET, "DATASET", evidence);
        double accuracy = detectAccuracy(pages, evidence);
        boolean baseline = detectKeyword(pages, "BASELINE", evidence,
                "baseline", "compared with", "comparison with", "state-of-the-art", "benchmark",
                "comparé à", "comparaison avec", "régression logistique");
        boolean statistics = detectKeyword(pages, "STATISTICS", evidence,
                "p-value", "p value", "p <", "p=", "confidence interval", "statistically significant",
                "anova", "t-test", "wilcoxon", "intervalle de confiance", "statistiquement significatif");
        boolean limitations = detectKeyword(pages, "LIMITATIONS", evidence,
                "limitation", "limitations", "future work", "future research", "threats to validity",
                "limite de cette étude", "limites de l'étude", "travaux futurs");
        boolean ethics = detectKeyword(pages, "ETHICS", evidence,
                "ethics committee", "ethical approval", "institutional review board", "informed consent",
                "comité d'éthique", "approbation éthique", "consentement éclairé");
        boolean anonymized = detectKeyword(pages, "ANONYMIZATION", evidence,
                "anonymized", "anonymised", "de-identified", "pseudonymized", "pseudonymised",
                "anonymisées", "anonymisé", "pseudonymisées");

        if (datasetSize == 0) warnings.add("Taille du dataset non détectée");
        if (accuracy == 0) warnings.add("Accuracy non détectée (une autre métrique est peut-être utilisée)");
        if (abstractText.isBlank()) warnings.add("Section Abstract/Résumé non détectée");

        Paper paper = new Paper(title, abstractText.isBlank() ? firstChars(fullText, 1200) : abstractText,
                datasetSize, baseline, statistics, limitations, ethics, anonymized, accuracy,
                file.getName(), pages.size(), evidence);
        return new Extraction(paper, fullText, warnings);
    }

    private int detectNumber(List<PageText> pages, Pattern pattern, String criterion, List<Evidence> evidence) {
        for (PageText page : pages) {
            Matcher matcher = pattern.matcher(page.text());
            if (matcher.find()) {
                int value = Integer.parseInt(matcher.group(1));
                evidence.add(new Evidence(criterion, page.number(), sentenceAround(page.text(), matcher.start()), .88));
                return value;
            }
        }
        return 0;
    }

    private double detectAccuracy(List<PageText> pages, List<Evidence> evidence) {
        for (PageText page : pages) {
            for (Pattern pattern : List.of(ACCURACY_AFTER, ACCURACY_BEFORE)) {
                Matcher matcher = pattern.matcher(page.text());
                if (matcher.find()) {
                    double value = Double.parseDouble(matcher.group(1).replace(',', '.')) / 100.0;
                    evidence.add(new Evidence("ACCURACY", page.number(), sentenceAround(page.text(), matcher.start()), .92));
                    return value;
                }
            }
        }
        return 0;
    }

    private boolean detectKeyword(List<PageText> pages, String criterion, List<Evidence> evidence, String... words) {
        for (PageText page : pages) {
            String lower = page.text().toLowerCase(Locale.ROOT);
            for (String word : words) {
                int index = lower.indexOf(word.toLowerCase(Locale.ROOT));
                if (index >= 0) {
                    evidence.add(new Evidence(criterion, page.number(), sentenceAround(page.text(), index), .80));
                    return true;
                }
            }
        }
        return false;
    }

    private String detectTitle(String firstPage) {
        return firstPage.lines().map(String::trim)
                .filter(this::validTitle).findFirst().orElse("Article scientifique sans titre détecté");
    }

    private boolean validTitle(String line) {
        if (line == null) return false;
        String lower = line.toLowerCase(Locale.ROOT).trim();
        return line.length() >= 18 && line.length() <= 240 &&
                !lower.startsWith("http") && !lower.contains("doi.org") &&
                !lower.matches(".*(journal|volume|copyright|proceedings).*20\\d{2}.*");
    }

    private String detectAbstract(String text) {
        Matcher matcher = Pattern.compile(
                "(?is)\\b(?:abstract|résumé)\\b\\s*[:.-]?\\s*(.{80,1800}?)(?=\\n\\s*(?:keywords?|mots[- ]clés|1\\.?\\s*introduction|introduction)\\b)")
                .matcher(text);
        return matcher.find() ? clean(matcher.group(1)) : "";
    }

    private static String sentenceAround(String text, int index) {
        int start = Math.max(0, text.lastIndexOf('\n', index) + 1);
        int end = text.indexOf('\n', index);
        if (end < 0) end = text.length();
        if (end - start < 45 && end < text.length()) {
            int following = text.indexOf('\n', end + 1);
            end = following < 0 ? text.length() : following;
        }
        String sentence = clean(text.substring(start, Math.min(text.length(), end)));
        return sentence.length() > 300 ? sentence.substring(0, 297) + "..." : sentence;
    }

    private static String clean(String text) { return text == null ? "" : text.replaceAll("[\\t ]+", " ").replaceAll("\\n{3,}", "\n\n").trim(); }
    private static String firstChars(String text, int max) { return text.length() <= max ? text : text.substring(0, max) + "..."; }
}
