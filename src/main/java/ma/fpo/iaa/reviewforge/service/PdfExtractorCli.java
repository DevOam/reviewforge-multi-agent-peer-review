package ma.fpo.iaa.reviewforge.service;

import ma.fpo.iaa.reviewforge.domain.Evidence;
import ma.fpo.iaa.reviewforge.domain.Paper;
import java.io.File;

public final class PdfExtractorCli {
    private PdfExtractorCli() {}
    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Usage: PdfExtractorCli article.pdf");
        PdfPaperExtractor.Extraction extraction = new PdfPaperExtractor().extract(new File(args[0]));
        Paper p = extraction.paper();
        System.out.println("TITLE=" + p.title());
        System.out.println("PAGES=" + p.pageCount());
        System.out.println("DATASET=" + p.datasetSize());
        System.out.printf("ACCURACY=%.1f%%%n", p.reportedAccuracy() * 100);
        System.out.println("BASELINE=" + p.hasBaseline());
        System.out.println("STATISTICS=" + p.hasStatisticalTest());
        System.out.println("LIMITATIONS=" + p.discussesLimitations());
        System.out.println("ETHICS=" + p.hasEthicsStatement());
        System.out.println("ANONYMIZED=" + p.anonymizedData());
        for (Evidence evidence : p.evidence())
            System.out.println("EVIDENCE=" + evidence.criterion() + "|" + evidence.display());
        extraction.warnings().forEach(w -> System.out.println("WARNING=" + w));
    }
}
