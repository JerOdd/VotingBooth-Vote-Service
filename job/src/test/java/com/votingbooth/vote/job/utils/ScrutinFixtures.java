package com.votingbooth.vote.job.utils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

// Real files from the L17 (23/10/2026) and L16 Scrutins archives, in src/test/resources/scrutins/<uid>.json.
public final class ScrutinFixtures {

    public static final List<String> L17 = List.of(
            "VTANR5L17V1",    // censure motion: only "pour" listed, official non-voters (10) differ from the list (21)
            "VTANR5L17V6",    // mise au point from a representative absent from the scrutin (PM842798)
            "VTANR5L17V12",   // ordinary, article; single-voter lists, non-voters MG/PAN/PSE, delegations
            "VTANR5L17V213",  // amendment
            "VTANR5L17V456",  // solemn, declaration of the government
            "VTANR5L17V1992", // mise au point with a dysfonctionnement block (singular "contre" key)
            "VTANR5L17V5280"  // amendment carrying objet.dossierLegislatif.dossierRef
    );
    public static final List<String> L16 = List.of(
            "VTANR5L16V1",    // censure motion labelled "scrutin public solennel"
            "VTANR5L16V3",    // mises au point; non-voters without parDelegation
            "VTANR5L16V81",   // solemn, final vote on the whole text
            "VTANR5L16V1927", // official counts differ from the nominative lists by one vote against
            "VTCGR5L16V1"     // Congrès (SSG), with senators: not an Assemblée scrutin
    );

    private ScrutinFixtures() {
    }

    public static byte[] read(String uid) {
        try (InputStream in = ScrutinFixtures.class.getResourceAsStream("/scrutins/" + uid + ".json")) {
            if (in == null) {
                throw new IllegalArgumentException("No fixture " + uid);
            }
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // A zip laid out like the real archive: json/<uid>.json.
    public static Path zip(Path directory, String name, List<String> uids) {
        return zip(directory, name, uids, null, null);
    }

    // Same, with one file's content replaced.
    public static Path zip(Path directory, String name, List<String> uids, String replacedUid, byte[] replacement) {
        Path archive = directory.resolve(name);
        try (OutputStream out = Files.newOutputStream(archive); ZipOutputStream zip = new ZipOutputStream(out)) {
            for (String uid : uids) {
                zip.putNextEntry(new ZipEntry("json/" + uid + ".json"));
                zip.write(uid.equals(replacedUid) ? replacement : read(uid));
                zip.closeEntry();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return archive;
    }

}
