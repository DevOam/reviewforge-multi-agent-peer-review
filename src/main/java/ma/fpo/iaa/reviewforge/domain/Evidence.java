package ma.fpo.iaa.reviewforge.domain;

import java.io.Serializable;

public record Evidence(String criterion, int page, String excerpt, double confidence)
        implements Serializable {
    public String display() {
        return "Page " + page + " - \"" + excerpt + "\"";
    }
}
