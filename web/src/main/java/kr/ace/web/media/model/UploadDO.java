package kr.ace.web.media.model;

import org.springframework.web.multipart.MultipartFile;

/** 브라우저가 올린 사진을 서비스 계층에 전달한다. */
public record UploadDO(MultipartFile photo) {}
