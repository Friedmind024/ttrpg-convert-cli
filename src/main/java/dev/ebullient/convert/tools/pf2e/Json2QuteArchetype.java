package dev.ebullient.convert.tools.pf2e;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.JsonNode;

import dev.ebullient.convert.tools.NodeReader;
import dev.ebullient.convert.tools.pf2e.qute.QuteArchetype;
import dev.ebullient.convert.tools.pf2e.qute.QuteFeat;

public class Json2QuteArchetype extends Json2QuteBase {

    public Json2QuteArchetype(Pf2eIndex index, Pf2eIndexType type, JsonNode rootNode) {
        super(index, type, rootNode);
    }

    @Override
    public QuteArchetype build() {
        List<String> tags = new ArrayList<>(sources.getSourceTags());
        List<String> text = new ArrayList<>();

        appendEntryToText(text, Field.entries.getFrom(rootNode), "##");
        appendFootnotes(text, 0);

        List<String> benefits = ArchetypeField.benefits.getListOfStrings(rootNode, tui());
        benefits.forEach(b -> tags.add(cfg().tagOf("archetype", "benefit", b)));

        int dedicationLevel = ArchetypeField.dedicationLevel.intOrDefault(rootNode, 2);

        return new QuteArchetype(sources, text, tags,
                collectTraitsFrom(rootNode),
                dedicationLevel,
                benefits,
                getFeatures(dedicationLevel));
    }

    List<String> getFeatures(int dedicationLevel) {
        List<String> extraFeats = ArchetypeField.extraFeats.getListOfStrings(rootNode, tui());
        Set<String> indexedFeats = index().featKeys(sources.getKey());

        List<QuteFeat> quteFeats = new ArrayList<>();
        List<String> featsRemaining = new ArrayList<>(extraFeats);

        if (indexedFeats != null) {
            indexedFeats.forEach(f -> {
                JsonNode feat = index.getIncludedNode(f);
                if (feat != null) {
                    String needle = f.substring("feat".length());
                    Optional<String> extra = extraFeats.stream()
                            // Sometimes there is a class note inserted: 10|Feat Name (Wizard)|Source
                            // if so, attempt to recognize both with and without
                            .flatMap(n -> (n.contains(" (")
                                    ? List.of(n, n.replaceAll(" \\(.*\\)", ""))
                                    : List.of(n)).stream())
                            // Find the key that matches the ending: Feat Name|Source
                            .filter(n -> n.endsWith(needle))
                            .findFirst();

                    // Find either the adjusted archetype field level or the Feat level
                    String level = Pf2eFeat.level.getTextOrDefault(feat, "1");
                    if (extra.isPresent()) {
                        String extraKey = extra.get();
                        level = extraKey.substring(0, extraKey.indexOf("|"));
                        featsRemaining.remove(extraKey);
                    }
                    quteFeats.add(createQuteFeat(feat, level));
                }
            });
        }

        featsRemaining.stream()
                .map(f -> findFeat(f))
                .filter(f -> f != null)
                .forEach(f -> quteFeats.add(f));

        quteFeats.sort((a, b) -> {
            int compare = Integer.compare(Integer.parseInt(a.level), Integer.parseInt(b.level));
            if (compare == 0) {
                String aName = a.getName().toLowerCase();
                String bName = b.getName().toLowerCase();
                if (aName.equals(bName)) {
                    return a.getSource().compareTo(b.getSource());
                } else if (aName.contains("dedication") && bName.contains("dedication")) {
                    return aName.compareTo(bName);
                } else if (aName.contains("dedication")) {
                    return -1;
                } else if (bName.contains("dedication")) {
                    return 1;
                }
                return aName.compareTo(bName);
            }
            return compare;
        });

        return quteFeats.stream()
                .map(x -> render(x))
                .collect(Collectors.toList());
    }

    QuteFeat createQuteFeat(JsonNode feat, String level) {
        Json2QuteFeat json2Qute = new Json2QuteFeat(index, Pf2eIndexType.feat, feat);
        return json2Qute.buildArchetype(sources.getName(), level);
    }

    QuteFeat findFeat(String levelKey) {
        String[] parts = levelKey.split("\\|");
        String key = Pf2eIndexType.feat.createKey(parts[1], parts[2]);
        JsonNode feat = index.getIncludedNode(key);
        if (feat == null && key.contains(" (")) {
            int pos = parts[1].indexOf(" (");
            key = Pf2eIndexType.feat.createKey(parts[1].substring(0, pos), parts[2]);
            feat = index.getIncludedNode(key);
        }
        if (feat == null) {
            tui().errorf("Could not find feat matching %s", levelKey);
            return null;
        }
        Json2QuteFeat json2Qute = new Json2QuteFeat(index, Pf2eIndexType.feat, feat);
        return json2Qute.buildArchetype(sources.getName(), parts[0]);
    }

    String render(QuteFeat quteFeat) {
        String rendered = tui().applyTemplate(quteFeat);
        int begin = rendered.indexOf("# ");
        List<String> inner = removePreamble(new ArrayList<>(
                List.of(rendered.substring(begin).split("\n"))));
        String backticks = nestedEmbed(inner);

        inner.add(0, "collapse: closed");
        inner.add(0, String.format("title: %s, Feat %s",
                quteFeat.getName(),
                quteFeat.level + (rendered.contains("> [!note] This version of ") ? "*" : "")));
        inner.add(0, backticks + "ad-embed-feat");
        inner.add(backticks);

        return String.join("\n", inner);
    }

    enum ArchetypeField implements NodeReader {
        benefits,
        dedicationLevel,
        extraFeats,
        miscTags,
    }
}
