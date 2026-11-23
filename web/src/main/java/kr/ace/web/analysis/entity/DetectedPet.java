package kr.ace.web.analysis.entity;

public record DetectedPet(int number, String species, double x1, double y1, double x2, double y2,
        double detectionScore, String breed, Double breedScore, String classificationStatus) {}
