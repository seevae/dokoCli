package com.dokocli.core.skill;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Two-layer skill injection:
 * Layer 1 (cheap): skill names + descriptions in system prompt (~100 tokens/skill)
 * Layer 2 (on demand): full skill body returned via load_skill tool_result
 * <p>
 * Scans skills/<name>/SKILL.md with YAML frontmatter.
 */
@Component
public class SkillLoader {

    private static final Logger log = LoggerFactory.getLogger(SkillLoader.class);
    private static final String SKILL_FILENAME = "SKILL.md";
    private static final Pattern FRONTMATTER_PATTERN =
            Pattern.compile("^---\\n(.*?)\\n---\\n(.*)", Pattern.DOTALL);

    private final Path skillsDir;
    private final Map<String, Skill> skills = new LinkedHashMap<>();

    public SkillLoader() {
        this.skillsDir = Paths.get("").toAbsolutePath().normalize().resolve("skills");
        loadAll();
    }

    private void loadAll() {
        if (!Files.exists(skillsDir) || !Files.isDirectory(skillsDir)) {
            log.info("Skills directory not found: {}, no skills loaded", skillsDir);
            return;
        }
        try (Stream<Path> paths = Files.walk(skillsDir)) {
            paths.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().equals(SKILL_FILENAME))
                    .sorted()
                    .forEach(this::loadSkillFile);
        } catch (IOException e) {
            log.warn("Failed to scan skills directory: {}", e.getMessage());
        }
        log.info("Loaded {} skills: {}", skills.size(), skills.keySet());
    }

    private void loadSkillFile(Path file) {
        try {
            String text = Files.readString(file, StandardCharsets.UTF_8);
            Map<String, String> meta = new LinkedHashMap<>();
            String body;

            Matcher matcher = FRONTMATTER_PATTERN.matcher(text);
            if (matcher.matches()) {
                for (String line : matcher.group(1).strip().split("\n")) {
                    int colon = line.indexOf(':');
                    if (colon >= 0) {
                        String key = line.substring(0, colon).strip();
                        String val = line.substring(colon + 1).strip();
                        meta.put(key, val);
                    }
                }
                body = matcher.group(2).strip();
            } else {
                body = text;
            }

            String name = meta.getOrDefault("name", file.getParent().getFileName().toString());
            skills.put(name, new Skill(meta, body, file.toString()));
            log.debug("Loaded skill '{}' from {}", name, file);
        } catch (IOException e) {
            log.warn("Failed to load skill file {}: {}", file, e.getMessage());
        }
    }

    /**
     * Layer 1: short descriptions for the system prompt.
     */
    public String getDescriptions() {
        if (skills.isEmpty()) {
            return "(no skills available)";
        }
        StringJoiner joiner = new StringJoiner("\n");
        for (Map.Entry<String, Skill> entry : skills.entrySet()) {
            String name = entry.getKey();
            Skill skill = entry.getValue();
            String desc = skill.meta().getOrDefault("description", "No description");
            String tags = skill.meta().getOrDefault("tags", "");
            String line = "  - " + name + ": " + desc;
            if (!tags.isEmpty()) {
                line += " [" + tags + "]";
            }
            joiner.add(line);
        }
        return joiner.toString();
    }

    /**
     * Layer 2: full skill body returned in tool_result.
     */
    public String getContent(String name) {
        Skill skill = skills.get(name);
        if (skill == null) {
            return "Error: Unknown skill '" + name + "'. Available: " + String.join(", ", skills.keySet());
        }
        return "<skill name=\"" + name + "\">\n" + skill.body() + "\n</skill>";
    }

    public Set<String> getSkillNames() {
        return Collections.unmodifiableSet(skills.keySet());
    }

    public boolean hasSkills() {
        return !skills.isEmpty();
    }

    /**
     * Internal record holding parsed skill data.
     */
    private record Skill(Map<String, String> meta, String body, String path) {
    }
}
