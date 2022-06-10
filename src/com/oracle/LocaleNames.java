package com.oracle;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.util.Currency;
import java.util.HexFormat;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LocaleNames {

    private static final Pattern LANG = Pattern.compile("(?<id>[a-z]{2,3})=.*");
    private static final Pattern RGN = Pattern.compile("(?<id>[A-Z]{2}|\\d{3})=.*");
    private static final Pattern SCPT = Pattern.compile("(?<id>[A-Z][a-z]{3})=.*");
    private static final String LNFILES = "LocaleNames(_*([^.]*))\\.properties";

    private static final Pattern CNAME = Pattern.compile("(?<id>[a-z]{3})=.*");
    private static final Pattern CSYMBOL = Pattern.compile("(?<id>[A-Z]{3})=.*");
    private static final String CNFILES = "CurrencyNames(_*([^.]*))\\.properties";

    private static Path OUTDIR;

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
        System.out.println("file: " + propertiesFile + ", loc: " + loc);

        try {
            var origLines = Files.readAllLines(propertiesFile);
            var lines = origLines.stream()
                    .map(l -> {
                        Matcher m1 = LANG.matcher(l);
                        Matcher m2 = RGN.matcher(l);
                        Matcher m3 = SCPT.matcher(l);

                        if (m1.matches()) {
                            String id = m1.group("id");
                            l = id + "=" +
                                    new Locale.Builder()
                                            .setLanguage(id)
                                            .build()
                                            .getDisplayLanguage(loc);
                        } else if (m2.matches()) {
                            String id = m2.group("id");
                            l = id + "=" +
                                    new Locale.Builder()
                                            .setRegion(id)
                                            .build()
                                            .getDisplayCountry(loc);
                        } else if (m3.matches()) {
                            String id = m3.group("id");
                            l = id + "=" +
                                    new Locale.Builder()
                                            .setScript(id)
                                            .build()
                                            .getDisplayScript(loc);
                        }
                        return l;
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
                    .map(l -> {
                        Matcher m1 = CNAME.matcher(l);
//                        Matcher m2 = CSYMBOL.matcher(l);

                        if (m1.matches()) {
                            String id = m1.group("id");
                            l = id + "=" + Currency.getInstance(id.toUpperCase(Locale.ROOT)).getDisplayName(loc);
//                        } else if (m2.matches()) {
//                            String id = m2.group("id");
//                            l = id + "=" + Currency.getInstance(id).getSymbol(loc);
                        }
                        return l;
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
}
