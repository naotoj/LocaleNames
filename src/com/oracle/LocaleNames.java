package com.oracle;

import sun.util.locale.provider.LocaleProviderAdapter;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Currency;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class LocaleNames {

    private static final Pattern LANG = Pattern.compile("(?<id>[a-z]{2,3})=.*");
    private static final Pattern RGN = Pattern.compile("(?<id>[A-Z]{2}|\\d{3})=.*");
    private static final Pattern SCPT = Pattern.compile("(?<id>[A-Z][a-z]{3})=.*");
    private static final String LNFILES = "LocaleNames(_*([^.]*))\\.properties";

    private static final Pattern CNAME = Pattern.compile("(?<id>[a-z]{3})=.*");
    private static final String CNFILES = "CurrencyNames(_*([^.]*))\\.properties";

    private static Path OUTDIR;

    // COMPAT supported locales
    private static List<Locale> LNLOCS = Arrays.asList(LocaleProviderAdapter.forJRE().getLocaleNameProvider().getAvailableLocales());
    private static List<Locale> CNLOCS = Arrays.asList(LocaleProviderAdapter.forJRE().getCurrencyNameProvider().getAvailableLocales());

    public static void main(String[] args) throws IOException {
        Path inDir = Paths.get(args[0]);
        OUTDIR = Paths.get(args[1], "resources");
        Files.createDirectories(OUTDIR);

        Files.walk(inDir, 1)
                .filter(f -> f.getFileName().toString().matches(".*" + LNFILES))
                .forEach(LocaleNames::LNconvert);
        Files.walk(inDir, 1)
                .filter(f -> f.getFileName().toString().matches(".*" + CNFILES))
                .forEach(LocaleNames::CNconvert);
    }

    static void LNconvert(Path propertiesFile) {
        var langTag = propertiesFile.getFileName().toString().replaceFirst(LNFILES, "$2").replaceAll("_", "-");
        var loc = switch (langTag) {
            case "no-NO-NY" -> Locale.of("no", "NO", "NY");
            default -> Locale.forLanguageTag(langTag);
        };
        var builder = new Locale.Builder();
        System.out.println("file: " + propertiesFile + ", loc: " + loc);

        try {
            var origLines = Files.readAllLines(propertiesFile);
            var lines = origLines.stream()
                    .flatMap(line -> {
                        Matcher m1 = LANG.matcher(line);
                        Matcher m2 = RGN.matcher(line);
                        Matcher m3 = SCPT.matcher(line);
                        builder.clear();

                        if (m1.matches()) {
                            String id = m1.group("id");
                            line = getLine(id, loc, LNLOCS, (l) -> builder.setLanguage(id).build().getDisplayLanguage(l));
                        } else if (m2.matches()) {
                            String id = m2.group("id");
                            line = getLine(id, loc, LNLOCS, (l) -> builder.setRegion(id).build().getDisplayCountry(l));
                        } else if (m3.matches()) {
                            String id = m3.group("id");
                            line = getLine(id, loc, LNLOCS, (l) -> builder.setScript(id).build().getDisplayScript(l));
                        }
                        return line != null ? Stream.of(line) : Stream.empty();
                    })
                    .map(LocaleNames::encode)
                    .toList();
            if (!origLines.equals(lines)) {
                lines = lines.stream()
                        .map(l -> l.replaceFirst("\\d{4}, Oracle and/or its affiliates", LocalDate.now().getYear() + ", Oracle and/or its affiliates"))
                        .toList();
            }
            Files.write(OUTDIR.resolve(propertiesFile.getFileName()), lines, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE);
        } catch (IOException ioe) {
            throw new UncheckedIOException(ioe);
        }
    }

    static void CNconvert(Path propertiesFile) {
        var langTag = propertiesFile.getFileName().toString().replaceFirst(CNFILES, "$2").replaceAll("_", "-");
        var loc = switch (langTag) {
            case "no-NO-NY" -> Locale.of("no", "NO", "NY");
            default -> Locale.forLanguageTag(langTag);
        };
        System.out.println("file: " + propertiesFile + ", loc: " + loc);

        try {
            var origLines = Files.readAllLines(propertiesFile);
            var lines = origLines.stream()
                    .flatMap(line -> {
                        Matcher m1 = CNAME.matcher(line);

                        if (m1.matches()) {
                            String id = m1.group("id");
                            line = getLine(id, loc, CNLOCS, (l) -> Currency.getInstance(id.toUpperCase(Locale.ROOT)).getDisplayName(l));
                        }
                        return line != null ? Stream.of(line) : Stream.empty();
                    })
                    .map(LocaleNames::encode)
                    .toList();
            if (!origLines.equals(lines)) {
                lines = lines.stream()
                        .map(l -> l.replaceFirst("\\d{4}, Oracle and/or its affiliates", LocalDate.now().getYear() + ", Oracle and/or its affiliates"))
                        .toList();
            }
            Files.write(OUTDIR.resolve(propertiesFile.getFileName()), lines, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE);
        } catch (IOException ioe) {
            throw new UncheckedIOException(ioe);
        }
    }

    static String encode(String str) {
        return str.codePoints()
                .mapToObj(c -> c < 0x80 ? Character.toString(c) : "\\u" + HexFormat.of().toHexDigits(c).substring(4))
                .collect(StringBuilder::new, StringBuilder::append, StringBuilder::append)
                .toString();
    }

    static String getLine(String id, Locale loc, List<Locale> supported, Function<Locale, String> getter) {
        var cands = ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES).getCandidateLocales("", loc);
        var parent = loc.equals(Locale.ROOT) ? Locale.ROOT :
                cands.get(IntStream.range(0, cands.size())
                        .filter(i -> cands.get(i).equals(loc))
                        .findFirst()
                        .orElseThrow() + 1);
        var lName = getter.apply(loc);

        if (loc.equals(Locale.ROOT) || !supported.contains(parent)) {
            return id + "=" + lName;
        } else {
            return lName.equals(getter.apply(parent)) ? null : id + "=" + lName;
        }
    }
}
