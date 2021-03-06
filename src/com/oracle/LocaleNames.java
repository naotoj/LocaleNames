/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package com.oracle;

import sun.util.locale.provider.LocaleProviderAdapter;
import sun.util.locale.provider.ResourceBundleBasedAdapter;

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
    private static final List<Locale> LNLOCS = Arrays.asList(LocaleProviderAdapter.forJRE().getLocaleNameProvider().getAvailableLocales());
    private static final List<Locale> CNLOCS = Arrays.asList(LocaleProviderAdapter.forJRE().getCurrencyNameProvider().getAvailableLocales());

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
                        String id = null;
                        Function<Locale, String> f = null;
                        builder.clear();

                        if (m1.matches()) {
                            id = m1.group("id");
                            f  = builder.setLanguage(id).build()::getDisplayLanguage;
                        } else if (m2.matches()) {
                            id = m2.group("id");
                            f  = builder.setRegion(id).build()::getDisplayCountry;
                        } else if (m3.matches()) {
                            id = m3.group("id");
                            f  = builder.setScript(id).build()::getDisplayScript;
                        }
                        if (id != null) {
                            line = getLine(id, loc, true, f);
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
                            line = getLine(id, loc, false, Currency.getInstance(id.toUpperCase(Locale.ROOT))::getDisplayName);
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

    static String getLine(String id, Locale loc, boolean isLocaleName, Function<Locale, String> getter) {
        var lName = getter.apply(loc);
        if (loc.equals(Locale.ROOT) || loc.getLanguage().equals("no") ||
            loc.getLanguage().equals("nb") || loc.getLanguage().equals("nn")) { // do not mess with Norwegian here
            return id + "=" + lName;
        } else {
            var cands = ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES).getCandidateLocales("", loc);
            var supported = isLocaleName ? LNLOCS : CNLOCS;
            var index = IntStream.range(0, cands.size())
                    .filter(i -> cands.get(i).equals(loc))
                    .findFirst()
                    .orElseThrow();
            var pLoc = cands.get(index + 1);
            String pName = null;
            if (supported.contains(pLoc)) {
                var ld = ((ResourceBundleBasedAdapter)LocaleProviderAdapter.forJRE()).getLocaleData();
                var rb = isLocaleName ? ld.getLocaleNames(pLoc) : ld.getCurrencyNames(pLoc);
                if (rb.containsKey(id)) {
                    pName = rb.getString(id);
                }
            }

            return lName.equals(pName) ? null : id + "=" + lName;
        }
    }
}
