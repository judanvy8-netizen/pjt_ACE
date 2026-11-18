package kr.ace.web.analysis.controller;

import java.util.Map;
import org.springframework.dao.DataAccessException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.ModelAndView;

@ControllerAdvice
public class RequestErrorHandler {
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ModelAndView tooLarge() {
        return new ModelAndView("notice", Map.of("message", "사진은 15MB 이하여야 합니다."),
                org.springframework.http.HttpStatus.PAYLOAD_TOO_LARGE);
    }

    @ExceptionHandler(DataAccessException.class)
    ModelAndView databaseFailure() {
        return new ModelAndView("notice", Map.of("message", "MySQL에 연결하거나 기록을 저장하지 못했습니다. DB 상태를 확인해 주세요."),
                org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE);
    }
}
