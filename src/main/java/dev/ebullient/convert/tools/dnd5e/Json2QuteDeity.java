package dev.ebullient.convert.tools.dnd5e;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;

import dev.ebullient.convert.qute.ImageRef;
import dev.ebullient.convert.qute.QuteDeity;
import dev.ebullient.convert.qute.QuteSource;
import dev.ebullient.convert.tools.IndexType;

public class Json2QuteDeity extends Json2QuteCommon {

    Json2QuteDeity(JsonIndex index, IndexType type, JsonNode jsonNode) {
        super(index, type, jsonNode);
    }

    @Override
    public QuteSource build() {

        List<String> tags = new ArrayList<>(sources.getSourceTags());

        String pantheon = getTextOrDefault(node, "pantheon", null);
        if (pantheon != null) {
            tags.add("deity/" + slugify(pantheon));
        }

        List<String> domains = new ArrayList<>();
        if (node.has("domains")) {
            node.withArray("domains").forEach(d -> {
                String domain = d.asText();
                tags.add("domain/" + slugify(domain));
                domains.add(domain);
            });
        }

        return new QuteDeity(sources,
                getName(),
                getSources().getSourceText(index.srdOnly()),
                findAndReplace(node, "altNames"),
                pantheon,
                dietyAlignment(),
                replaceText(getTextOrEmpty(node, "title")),
                replaceText(getTextOrEmpty(node, "category")),
                String.join(", ", domains),
                replaceText(getTextOrEmpty(node, "province")),
                replaceText(getTextOrEmpty(node, "symbol")),
                getSymbolImage(),
                getText("##"),
                tags);
    }

    String dietyAlignment() {
        ArrayNode a1 = node.withArray("alignment");
        if (a1.size() == 0) {
            return "Unaligned";
        }
        String choices = a1.toString().replaceAll("[^LCNEGAUXY]", "");
        return mapAlignmentToString(choices);
    }

    ImageRef getSymbolImage() {
        if (node.has("symbolImg")) {
            JsonNode symbolImg = node.get("symbolImg");
            try {
                JsonMediaHref mediaHref = mapper().treeToValue(symbolImg, JsonMediaHref.class);
                if (mediaHref.href.path != null) {
                    Path sourcePath = Path.of("img", mediaHref.href.path);
                    String fileName = sourcePath.getFileName().toString();
                    int i = fileName.lastIndexOf('.');
                    Path target = Path.of("deities", "img", slugify(fileName.substring(0, i)) + fileName.substring(i));
                    String title = mediaHref.title == null ? "" : mediaHref.title;

                    return new ImageRef.Builder()
                            .setSourcePath(sourcePath)
                            .setTargetPath(index().compendiumPath(), target)
                            .setMarkdownPath(title, index().compendiumRoot())
                            .build();
                }
            } catch (JsonProcessingException | IllegalArgumentException e) {
                tui().errorf("Unable to read media reference from %s", symbolImg.toPrettyString());
            }
        }
        return null;
    }
}
