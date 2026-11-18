package kr.ace.web.truth.entity;

public record Evaluation(int version, int cats, int dogs, int catError, int dogError,
        boolean matched, int breedCorrect, int breedTotal, int catDiff, int dogDiff, int breedUnsupported) {
    public kr.ace.web.evaluation.Verdict verdict() {
        return new kr.ace.web.evaluation.Verdict(catDiff, dogDiff, breedCorrect, breedTotal,
                breedUnsupported, cats + dogs + catDiff + dogDiff, 0);
    }

    public String countLabel() {
        return kr.ace.web.evaluation.Verdict.countLabel(cats, dogs, catDiff, dogDiff);
    }
}
