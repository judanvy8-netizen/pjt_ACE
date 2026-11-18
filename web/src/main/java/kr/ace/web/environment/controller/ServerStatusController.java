package kr.ace.web.environment.controller;

import kr.ace.web.environment.model.ServerStatusDO;
import kr.ace.web.environment.service.ServerStatusService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/server-status")
public class ServerStatusController {
    private final ServerStatusService service;

    public ServerStatusController(ServerStatusService service) { this.service = service; }

    @GetMapping("/web")
    public ResponseEntity<ServerStatusDO> web() {
        return response(new ServerStatusDO("up", "웹 상태 API 응답을 확인했습니다."));
    }

    @GetMapping("/python")
    public ResponseEntity<ServerStatusDO> python() { return response(service.python()); }

    @GetMapping("/database")
    public ResponseEntity<ServerStatusDO> database() { return response(service.database()); }

    private ResponseEntity<ServerStatusDO> response(ServerStatusDO status) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(status);
    }
}
