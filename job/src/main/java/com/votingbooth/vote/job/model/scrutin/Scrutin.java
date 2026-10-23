package com.votingbooth.vote.job.model.scrutin;

import com.fasterxml.jackson.annotation.JsonAlias;

import java.time.LocalDate;
import java.util.List;

// One file of the Assemblée nationale's "Scrutins" archive (json/<uid>.json, under a "scrutin" root), limited to the fields
// the job uses. Numbers and booleans are strings in the source; Jackson coerces them.
public record Scrutin(String uid, Integer numero, Integer legislature, String sessionRef, String seanceRef,
                      LocalDate dateScrutin, TypeVote typeVote, Sort sort, String titre, Demandeur demandeur, Objet objet,
                      SyntheseVote syntheseVote, VentilationVotes ventilationVotes, Positions miseAuPoint) {

    public record TypeVote(String codeTypeVote) {
    }

    public record Sort(String code) {
    }

    public record Demandeur(String texte) {
    }

    // referenceLegislative is always empty; dossierLegislatif is filled since April 2025 only.
    public record Objet(String libelle, DossierLegislatif dossierLegislatif) {
    }

    public record DossierLegislatif(String dossierRef) {
    }

    public record SyntheseVote(Integer nombreVotants, Integer suffragesExprimes, Integer nbrSuffragesRequis, Decompte decompte) {
    }

    public record Decompte(Integer pour, Integer contre, Integer abstentions, Integer nonVotants) {
    }

    public record VentilationVotes(Organe organe) {
    }

    public record Organe(Groupes groupes) {
    }

    public record Groupes(List<Groupe> groupe) {
    }

    public record Groupe(String organeRef, Integer nombreMembresGroupe, GroupeVote vote) {
    }

    public record GroupeVote(String positionMajoritaire, Decompte decompteVoix, Positions decompteNominatif) {
    }

    // The representatives listed under each position. The source writes "pours" or "pour" (Congrès scrutins and the
    // dysfonctionnement block), and wraps some lists in a two-element array whose first element is null.
    // In a miseAuPoint, dysfonctionnement holds the same lists for votes blamed on the voting machine.
    public record Positions(@JsonAlias("pour") List<Votants> pours,
                            @JsonAlias("contre") List<Votants> contres,
                            @JsonAlias("abstention") List<Votants> abstentions,
                            @JsonAlias("nonVotant") List<Votants> nonVotants,
                            List<Votants> nonVotantsVolontaires,
                            Positions dysfonctionnement) {
    }

    public record Votants(List<Votant> votant) {
    }

    // causePositionVote explains a non-vote: MG (member of the government), PAN (President of the Assemblée), PSE (presiding the sitting).
    public record Votant(String acteurRef, String mandatRef, Boolean parDelegation, String causePositionVote) {
    }

}
