package com.votingbooth.vote.job.utils;

import com.votingbooth.vote.core.model.entity.NonVotingReason;
import com.votingbooth.vote.core.model.entity.VoteKind;
import com.votingbooth.vote.core.model.entity.VotePosition;
import com.votingbooth.vote.core.model.entity.VoteResult;
import com.votingbooth.vote.core.model.entity.VoteType;
import com.votingbooth.vote.job.model.scrutin.Scrutin;
import com.votingbooth.vote.job.model.scrutin.ScrutinFile;
import com.votingbooth.vote.job.model.sync.ParsedScrutin;
import org.springframework.stereotype.Component;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// Turns a file of the "Scrutins" archive into a ParsedScrutin. A file that lacks what a scrutin needs, or uses a value the
// job doesn't know, is rejected with an IllegalArgumentException rather than stored half understood.
@Component
public class ScrutinParser {

    private final JsonMapper mapper = JsonMapper.builder()
            // The XML→JSON conversion turns a one-element list into a plain object.
            .enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    private final ScrutinKindClassifier kindClassifier;

    public ScrutinParser(ScrutinKindClassifier kindClassifier) {
        this.kindClassifier = kindClassifier;
    }

    public ParsedScrutin parse(byte[] json) {
        Scrutin scrutin = mapper.readValue(json, ScrutinFile.class).scrutin();
        if (scrutin == null || scrutin.uid() == null) {
            throw new IllegalArgumentException("Not a scrutin file");
        }
        String uid = scrutin.uid();
        if (scrutin.typeVote() == null || scrutin.sort() == null || scrutin.titre() == null || scrutin.syntheseVote() == null
                || scrutin.syntheseVote().decompte() == null || scrutin.ventilationVotes() == null
                || scrutin.ventilationVotes().organe() == null || scrutin.ventilationVotes().organe().groupes() == null) {
            throw new IllegalArgumentException("Scrutin " + uid + " lacks its type, result, title, counts or groups");
        }
        VoteType voteType = voteType(uid, scrutin.typeVote().codeTypeVote());
        Scrutin.SyntheseVote synthese = scrutin.syntheseVote();
        Scrutin.Decompte decompte = synthese.decompte();
        String normalizedTitle = kindClassifier.normalize(scrutin.titre());
        VoteKind kind = kindClassifier.classify(normalizedTitle);

        List<Scrutin.Groupe> groupes = orEmpty(scrutin.ventilationVotes().organe().groupes().groupe());
        List<ParsedScrutin.GroupVote> groups = new ArrayList<>();
        for (Scrutin.Groupe groupe : groupes) {
            groups.add(groupVote(uid, voteType, groupe));
        }
        // A few scrutins give every group the same ref (PO0): their groups can't be told apart, so none is kept.
        boolean groupsKnown = groups.stream().map(ParsedScrutin.GroupVote::groupExternalId).distinct().count() == groups.size();
        List<ParsedScrutin.RepresentativeVote> votes = new ArrayList<>();
        Set<String> mandates = new HashSet<>();
        for (Scrutin.Groupe groupe : groupes) {
            Scrutin.Positions positions = groupe.vote().decompteNominatif();
            if (positions == null) {
                continue;
            }
            String group = groupsKnown ? groupe.organeRef() : null;
            forEachVotant(positions, (position, votant) -> {
                if (!mandates.add(required(uid, votant.mandatRef(), "mandatRef"))) {
                    throw new IllegalArgumentException("Scrutin " + uid + " lists mandate " + votant.mandatRef() + " twice");
                }
                votes.add(new ParsedScrutin.RepresentativeVote(votant.mandatRef(), required(uid, votant.acteurRef(), "acteurRef"),
                        group, position, Boolean.TRUE.equals(votant.parDelegation()),
                        nonVotingReason(uid, votant.causePositionVote())));
            });
        }
        if (!groupsKnown) {
            groups = List.of();
        }

        return new ParsedScrutin(uid, required(uid, scrutin.legislature(), "legislature"), required(uid, scrutin.numero(), "numero"),
                required(uid, scrutin.dateScrutin(), "dateScrutin"), required(uid, scrutin.sessionRef(), "sessionRef"),
                required(uid, scrutin.seanceRef(), "seanceRef"), voteType, result(uid, scrutin.sort().code()),
                scrutin.titre(), normalizedTitle, kind,
                scrutin.demandeur() == null ? null : scrutin.demandeur().texte(),
                scrutin.objet() == null || scrutin.objet().dossierLegislatif() == null ? null : scrutin.objet().dossierLegislatif().dossierRef(),
                required(uid, synthese.nombreVotants(), "nombreVotants"), required(uid, synthese.suffragesExprimes(), "suffragesExprimes"),
                required(uid, synthese.nbrSuffragesRequis(), "nbrSuffragesRequis"),
                required(uid, decompte.pour(), "pour"), required(uid, decompte.contre(), "contre"),
                required(uid, decompte.abstentions(), "abstentions"), required(uid, decompte.nonVotants(), "nonVotants"),
                groups, votes, corrections(uid, scrutin.miseAuPoint()));
    }

    // On a censure motion only the votes for it are cast, so the source's "pour" majority of every group means nothing.
    private ParsedScrutin.GroupVote groupVote(String uid, VoteType voteType, Scrutin.Groupe groupe) {
        if (groupe.organeRef() == null || groupe.vote() == null || groupe.vote().decompteVoix() == null) {
            throw new IllegalArgumentException("Scrutin " + uid + " has a group without its ref or counts");
        }
        Scrutin.Decompte voix = groupe.vote().decompteVoix();
        VotePosition majority = voteType == VoteType.MOC ? null : majorityPosition(uid, groupe.vote().positionMajoritaire());
        return new ParsedScrutin.GroupVote(groupe.organeRef(), required(uid, groupe.nombreMembresGroupe(), "nombreMembresGroupe"),
                majority, required(uid, voix.pour(), "pour"), required(uid, voix.contre(), "contre"),
                required(uid, voix.abstentions(), "abstentions"), required(uid, voix.nonVotants(), "nonVotants"));
    }

    private List<ParsedScrutin.Correction> corrections(String uid, Scrutin.Positions miseAuPoint) {
        List<ParsedScrutin.Correction> corrections = new ArrayList<>();
        if (miseAuPoint == null) {
            return corrections;
        }
        Set<String> mandates = new HashSet<>();
        addCorrections(uid, miseAuPoint, false, mandates, corrections);
        if (miseAuPoint.dysfonctionnement() != null) {
            addCorrections(uid, miseAuPoint.dysfonctionnement(), true, mandates, corrections);
        }
        return corrections;
    }

    private static void addCorrections(String uid, Scrutin.Positions positions, boolean malfunction, Set<String> mandates,
                                       List<ParsedScrutin.Correction> corrections) {
        forEachVotant(positions, (position, votant) -> {
            if (!mandates.add(required(uid, votant.mandatRef(), "mandatRef"))) {
                throw new IllegalArgumentException("Scrutin " + uid + " has two mises au point for mandate " + votant.mandatRef());
            }
            corrections.add(new ParsedScrutin.Correction(votant.mandatRef(), required(uid, votant.acteurRef(), "acteurRef"),
                    position, malfunction));
        });
    }

    private static void forEachVotant(Scrutin.Positions positions, PositionedVotant consumer) {
        forEach(positions.pours(), VotePosition.FOR, consumer);
        forEach(positions.contres(), VotePosition.AGAINST, consumer);
        forEach(positions.abstentions(), VotePosition.ABSTAIN, consumer);
        forEach(positions.nonVotants(), VotePosition.NON_VOTING, consumer);
        forEach(positions.nonVotantsVolontaires(), VotePosition.NON_VOTING, consumer);
    }

    // Lists may hold null elements (e.g. [null, {"votant": ...}] in a miseAuPoint).
    private static void forEach(List<Scrutin.Votants> lists, VotePosition position, PositionedVotant consumer) {
        for (Scrutin.Votants votants : orEmpty(lists)) {
            if (votants == null) {
                continue;
            }
            for (Scrutin.Votant votant : orEmpty(votants.votant())) {
                if (votant != null) {
                    consumer.accept(position, votant);
                }
            }
        }
    }

    private static VoteType voteType(String uid, String code) {
        if ("SPO".equals(code) || "SPS".equals(code) || "MOC".equals(code)) {
            return VoteType.valueOf(code);
        }
        throw new IllegalArgumentException("Scrutin " + uid + " has an unknown vote type " + code);
    }

    private static VoteResult result(String uid, String code) {
        return switch (code == null ? "" : code) {
            case "adopté" -> VoteResult.ADOPTED;
            case "rejeté" -> VoteResult.REJECTED;
            default -> throw new IllegalArgumentException("Scrutin " + uid + " has an unknown result " + code);
        };
    }

    private static VotePosition majorityPosition(String uid, String position) {
        return switch (position == null ? "" : position) {
            case "pour" -> VotePosition.FOR;
            case "contre" -> VotePosition.AGAINST;
            case "abstention" -> VotePosition.ABSTAIN;
            default -> throw new IllegalArgumentException("Scrutin " + uid + " has an unknown group majority " + position);
        };
    }

    private static NonVotingReason nonVotingReason(String uid, String cause) {
        if (cause == null) {
            return null;
        }
        return switch (cause) {
            case "MG" -> NonVotingReason.GOVERNMENT_MEMBER;
            case "PAN" -> NonVotingReason.ASSEMBLY_PRESIDENT;
            case "PSE" -> NonVotingReason.SITTING_PRESIDENT;
            default -> throw new IllegalArgumentException("Scrutin " + uid + " has an unknown non-voting cause " + cause);
        };
    }

    private static <T> T required(String uid, T value, String field) {
        if (value == null) {
            throw new IllegalArgumentException("Scrutin " + uid + " lacks " + field);
        }
        return value;
    }

    private static <T> List<T> orEmpty(List<T> list) {
        return list == null ? List.of() : list;
    }

    @FunctionalInterface
    private interface PositionedVotant {
        void accept(VotePosition position, Scrutin.Votant votant);
    }

}
