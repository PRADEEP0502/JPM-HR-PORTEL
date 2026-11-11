package com.jpm.hrms.backup;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class BackupController {
    private final DailyBackupService backups;
    private final RecycleBinService recycleBin;

    public BackupController(DailyBackupService backups, RecycleBinService recycleBin) {
        this.backups = backups;
        this.recycleBin = recycleBin;
    }

    @GetMapping("/backups")
    public List<DailyBackupService.BackupInfo> backups() {
        return backups.list();
    }

    @PostMapping("/backups")
    public DailyBackupService.BackupInfo backupNow(Authentication auth) {
        return backups.backup(auth.getName());
    }

    @GetMapping("/backups/{id}/download")
    public ResponseEntity<byte[]> download(@PathVariable String id) {
        DailyBackupService.BackupFile f = backups.download(id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(f.fileName()).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(f.content());
    }

    @GetMapping("/recycle-bin")
    public List<RecycleBinService.Batch> recycleBin() {
        return recycleBin.list();
    }

    @PostMapping("/recycle-bin/{id}/restore")
    public RecycleBinService.RestoreResult restore(@PathVariable String id) {
        return recycleBin.restore(id);
    }
}
