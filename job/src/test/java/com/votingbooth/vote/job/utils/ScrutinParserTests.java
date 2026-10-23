package com.votingbooth.vote.job.utils;

import com.votingbooth.vote.core.model.entity.NonVotingReason;
import com.votingbooth.vote.core.model.entity.VoteKind;
import com.votingbooth.vote.core.model.entity.VotePosition;
import com.votingbooth.vote.core.model.entity.VoteResult;
import com.votingbooth.vote.core.model.entity.VoteType;
import com.votingbooth.vote.job.model.sync.ParsedScrutin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ScrutinParserTests {

    private ScrutinParser parser;

    @BeforeEach
    void setUp() {
        parser = new ScrutinParser(new ScrutinKindClassifier());
    }

    @Test
    void ordinaryScrutin() {
        ParsedScrutin scrutin = parse("VTANR5L17V12");

        assertEquals("VTANR5L17V12", scrutin.externalId());
        assertEquals(17, scrutin.legislature());
        assertEquals(12, scrutin.number());
        assertEquals(LocalDate.of(2024, 10, 22), scrutin.date());
        assertEquals("SCR5A2025O1", scrutin.sessionRef());
        assertEquals("RUANR5L17S2025IDS28586", scrutin.sittingRef());
        assertEquals(VoteType.SPO, scrutin.voteType());
        assertEquals(VoteResult.ADOPTED, scrutin.result());
        assertEquals(VoteKind.ARTICLE, scrutin.kind());
        assertEquals("l'article 2 du projet de loi de finances pour 2025 (première lecture).", scrutin.title());
        assertEquals("Président du groupe \"Ensemble pour la République\"", scrutin.requester());
        assertNull(scrutin.dossierExternalId());
        assertEquals(List.of(130, 107, 54, 104, 3, 23, 4), List.of(scrutin.voters(), scrutin.expressed(),
                scrutin.requiredMajority(), scrutin.countFor(), scrutin.countAgainst(), scrutin.countAbstain(),
                scrutin.countNonVoting()));

        assertEquals(12, scrutin.groups().size());
        assertEquals(new ParsedScrutin.GroupVote("PO845401", 125, VotePosition.FOR, 36, 1, 0, 0), scrutin.groups().getFirst());
        assertEquals(134, scrutin.votes().size());
    }

    @Test
    void singleVoterIsAnObjectNotAList() {
        ParsedScrutin scrutin = parse("VTANR5L17V12");

        assertEquals(new ParsedScrutin.RepresentativeVote("PM842579", "PA840967", "PO845401", VotePosition.AGAINST, false, null),
                vote(scrutin, "PM842579"));
    }

    @Test
    void delegationAndNonVotingReasons() {
        ParsedScrutin scrutin = parse("VTANR5L17V12");

        assertTrue(vote(scrutin, "PM842606").byDelegation());
        assertEquals(new ParsedScrutin.RepresentativeVote("PM843467", "PA721908", "PO845407", VotePosition.NON_VOTING, false,
                NonVotingReason.ASSEMBLY_PRESIDENT), vote(scrutin, "PM843467"));
        assertEquals(NonVotingReason.GOVERNMENT_MEMBER, vote(scrutin, "PM843431").nonVotingReason());
        assertEquals(NonVotingReason.SITTING_PRESIDENT, vote(scrutin, "PM843701").nonVotingReason());
    }

    @Test
    void solemnScrutin() {
        ParsedScrutin scrutin = parse("VTANR5L17V456");

        assertEquals(VoteType.SPS, scrutin.voteType());
        assertEquals(VoteResult.ADOPTED, scrutin.result());
        assertEquals(VoteKind.OTHER, scrutin.kind());
        assertEquals("Gouvernement", scrutin.requester());
        assertEquals(485 + 69 + 1 + 1, scrutin.votes().size());
    }

    @Test
    void censureMotion() {
        ParsedScrutin scrutin = parse("VTANR5L17V1");

        assertEquals(VoteType.MOC, scrutin.voteType());
        assertEquals(VoteResult.REJECTED, scrutin.result());
        assertEquals(VoteKind.MOTION, scrutin.kind());
        assertNull(scrutin.requester());
        assertEquals(0, scrutin.countAgainst());
        assertTrue(scrutin.groups().stream().allMatch(group -> group.majorityPosition() == null));
        assertEquals(197, scrutin.votes().stream().filter(vote -> vote.position() == VotePosition.FOR).count());
        assertTrue(scrutin.votes().stream().noneMatch(vote -> vote.position() == VotePosition.AGAINST));
    }

    @Test
    void officialCountsAreKeptOverTheLists() {
        ParsedScrutin censure = parse("VTANR5L17V1");
        assertEquals(10, censure.countNonVoting());
        assertEquals(21, censure.votes().stream().filter(vote -> vote.position() == VotePosition.NON_VOTING).count());

        ParsedScrutin scrutin = parse("VTANR5L16V1927");
        assertEquals(17, scrutin.countAgainst());
        assertEquals(16, scrutin.votes().stream().filter(vote -> vote.position() == VotePosition.AGAINST).count());
    }

    @Test
    void censureMotionIsKnownByItsCodeNotItsLabel() {
        ParsedScrutin scrutin = parse("VTANR5L16V1");

        assertEquals(VoteType.MOC, scrutin.voteType());
        assertEquals("Conférence des Présidents", scrutin.requester());
    }

    @Test
    void miseAuPointFromARepresentativeAbsentFromTheScrutin() {
        ParsedScrutin scrutin = parse("VTANR5L17V6");

        assertEquals(4, scrutin.corrections().size());
        assertTrue(scrutin.corrections().contains(new ParsedScrutin.Correction("PM842798", "PA841207", VotePosition.FOR, false)));
        assertTrue(scrutin.votes().stream().noneMatch(vote -> vote.mandateExternalId().equals("PM842798")));
        assertTrue(scrutin.corrections().contains(new ParsedScrutin.Correction("PM840246", "PA793322", VotePosition.NON_VOTING, false)));
    }

    @Test
    void miseAuPointWithMalfunction() {
        ParsedScrutin scrutin = parse("VTANR5L17V1992");

        assertEquals(List.of(
                new ParsedScrutin.Correction("PM843647", "PA721750", VotePosition.AGAINST, false),
                new ParsedScrutin.Correction("PM842771", "PA342384", VotePosition.AGAINST, true),
                new ParsedScrutin.Correction("PM842345", "PA793342", VotePosition.NON_VOTING, true)
        ), scrutin.corrections());
    }

    @Test
    void noMiseAuPoint() {
        assertEquals(List.of(), parse("VTANR5L17V456").corrections());
    }

    @Test
    void nonVotersWithoutDelegationFlag() {
        ParsedScrutin scrutin = parse("VTANR5L16V3");

        assertEquals(165 + 205 + 5 + 1, scrutin.votes().size());
        assertTrue(scrutin.votes().stream().filter(vote -> vote.position() == VotePosition.NON_VOTING)
                .noneMatch(ParsedScrutin.RepresentativeVote::byDelegation));
        assertEquals(5, scrutin.corrections().size());
    }

    @Test
    void dossierReference() {
        ParsedScrutin scrutin = parse("VTANR5L17V5280");

        assertEquals("DLR5L17N51655", scrutin.dossierExternalId());
        assertEquals(VoteKind.AMENDMENT, scrutin.kind());
    }

    @Test
    void finalVote() {
        ParsedScrutin scrutin = parse("VTANR5L16V81");

        assertEquals(VoteType.SPS, scrutin.voteType());
        assertEquals(VoteKind.FINAL_TEXT, scrutin.kind());
    }

    // One of the 12 L17 scrutins of 2 December 2024 that give every group the ref PO0.
    @Test
    void groupsThatCannotBeToldApartAreDropped() {
        ParsedScrutin scrutin = parse("VTANR5L17V495");

        assertEquals(List.of(), scrutin.groups());
        assertEquals(84 + 56 + 5 + 2, scrutin.votes().size()); // for, against, abstentions, non-voting
        assertTrue(scrutin.votes().stream().allMatch(vote -> vote.groupExternalId() == null));
    }

    @Test
    void congressScrutinIsRejected() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> parse("VTCGR5L16V1"));
        assertTrue(e.getMessage().contains("SSG"));
    }

    @Test
    void incompleteScrutinIsRejected() {
        byte[] json = "{\"scrutin\": {\"uid\": \"VTANR5L17V9999\", \"titre\": \"l'ensemble\"}}".getBytes(StandardCharsets.UTF_8);

        assertThrows(IllegalArgumentException.class, () -> parser.parse(json));
    }

    @Test
    void unknownPositionIsRejected() {
        String json = new String(ScrutinFixtures.read("VTANR5L17V12"), StandardCharsets.UTF_8)
                .replace("\"positionMajoritaire\": \"abstention\"", "\"positionMajoritaire\": \"blanc\"");

        assertThrows(IllegalArgumentException.class, () -> parser.parse(json.getBytes(StandardCharsets.UTF_8)));
    }

    private ParsedScrutin parse(String uid) {
        return parser.parse(ScrutinFixtures.read(uid));
    }

    private static ParsedScrutin.RepresentativeVote vote(ParsedScrutin scrutin, String mandate) {
        return scrutin.votes().stream().filter(vote -> vote.mandateExternalId().equals(mandate)).findFirst().orElseThrow();
    }

}
