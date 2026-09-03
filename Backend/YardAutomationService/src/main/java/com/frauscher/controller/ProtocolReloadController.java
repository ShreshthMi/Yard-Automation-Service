package com.frauscher.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.frauscher.protocol.ProtocolRegistry;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * GET /api/protocol/current -> what's loaded right now (path, row count, yard
 * count) POST /api/protocol/reload -> { "path": "/new/path.xlsx" } (or no body
 * / omit "path" to just re-read the current path, e.g. after editing it in
 * place)
 *
 * A failed reload (bad path, malformed sheet) returns 400 with the reason and
 * leaves whatever was already loaded untouched and fully working - it never
 * disrupts live traffic.
 */
@RestController
@RequestMapping("/api/protocol")
@CrossOrigin(origins = "*") // local dev/test convenience; tighten for production
public class ProtocolReloadController {

	public record ReloadRequest(String path) {
	}

	private final ProtocolRegistry protocolRegistry;

	public ProtocolReloadController(ProtocolRegistry protocolRegistry) {
		this.protocolRegistry = protocolRegistry;
	}

	@GetMapping("/current")
	public Map<String, Object> current() {
		return describe(protocolRegistry.current());
	}

	@PostMapping("/reload")
	public Map<String, Object> reload(@RequestBody(required = false) ReloadRequest request) {
		String path = (request != null && request.path() != null && !request.path().isBlank()) ? request.path()
				: protocolRegistry.current().sourcePath();
		try {
			protocolRegistry.reload(path);
		} catch (IOException e) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					"Failed to load \"" + path + "\": " + e.getMessage());
		}
		Map<String, Object> result = describe(protocolRegistry.current());
		result.put("reloaded", true);
		return result;
	}

	private Map<String, Object> describe(ProtocolRegistry.Snapshot snapshot) {
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("path", snapshot.sourcePath());
		result.put("coreRowCount", snapshot.coreRows().size());
		result.put("yardCount", snapshot.yards().size());
		return result;
	}
}