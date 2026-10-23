package com.votingbooth.vote.job.utils;

import com.votingbooth.vote.core.model.entity.VoteKind;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

// Titles from the L16 and L17 archives, typos included.
public class ScrutinKindClassifierTests {

    private ScrutinKindClassifier classifier;

    @BeforeEach
    void setUp() {
        classifier = new ScrutinKindClassifier();
    }

    @Test
    void normalize() {
        assertEquals("l'ensemble du projet de loi autorisant l'approbation de la convention d'entraide",
                classifier.normalize("  l'ensemble du projet de loi autorisant l’approbation de la convention d’entraide "));
        assertEquals("l'article 2 du projet de loi", classifier.normalize("L'article 2  du projet de loi"));
    }

    @Test
    void amendments() {
        assertKind(VoteKind.AMENDMENT, "l'amendement n° 80 de Mme Thiébault-Martinez et l'amendement identique suivant de suppression de l'article 6");
        assertKind(VoteKind.AMENDMENT, "l'amendement de suppression n° 16 de M. Di Filippo et les amendement identiques suivants");
        assertKind(VoteKind.AMENDMENT, "le sous-amendement n° 223 de M. Kerbrat à l'amendement n° 87 de Mme Faucillon à l'article 2");
        assertKind(VoteKind.AMENDMENT, "le sous-amendmeent n° 196 de M. Léaument à l'amendement n° 109 de M. Boudié à l'article 3");
        assertKind(VoteKind.AMENDMENT, "l'amenedement n° 187 de M. Renault à l'article 35 (examen prioritaire) du projet de loi");
    }

    @Test
    void finalTexts() {
        assertKind(VoteKind.FINAL_TEXT, "l'ensemble de la proposition de loi visant à lutter contre les déserts médicaux (première lecture).");
        assertKind(VoteKind.FINAL_TEXT, "l’ensemble du projet de loi d'urgence pour Mayotte (première lecture).");
        assertKind(VoteKind.FINAL_TEXT, "l'article unique de la proposition de résolution tendant à la création d'une commission d'enquête");
        assertKind(VoteKind.FINAL_TEXT, "la proposition de résolution visant à suspendre toute nouvelle création d'instances administratives");
        assertKind(VoteKind.FINAL_TEXT, "le projet de loi autorisant l'approbation de l'accord entre le gouvernement de la République française");
    }

    @Test
    void articles() {
        assertKind(VoteKind.ARTICLE, "l'article 14 de la proposition de loi portant création d'un statut de l'élu local (première lecture).");
        assertKind(VoteKind.ARTICLE, "l'article premier de la proposition de loi visant à faciliter le maintien en rétention");
        assertKind(VoteKind.ARTICLE, "l'article liminaire du projet de loi de finances pour 2025 (première lecture).");
        assertKind(VoteKind.ARTICLE, "l'aticle 6 du projet de loi sur la justice criminelle et le respect des victimes (première lecture).");
    }

    @Test
    void parts() {
        assertKind(VoteKind.PART, "la première partie du projet de loi de finances pour 2026 (première lecture).");
        assertKind(VoteKind.PART, "la deuxième partie projet de loi de financement de la sécurité sociale pour 2026 (nouvelle lecture).");
        assertKind(VoteKind.PART, "la troisième partie du projet de loi de financement de la sécurité sociale pour 2026 (nouvelle lecture).");
    }

    @Test
    void motions() {
        assertKind(VoteKind.MOTION, "la motion de censure déposée en application de l'article 49, alinéa 3, de la Constitution");
        assertKind(VoteKind.MOTION, "la motion de rejet préalable déposée par Mme Mathilde Panot");
        assertKind(VoteKind.MOTION, "sur la motion de rejet préalable, déposée par Mme Mathilde Panot, de la proposition de loi");
    }

    @Test
    void procedural() {
        assertKind(VoteKind.PROCEDURAL, "la demande de suspension de séance présentée par M. Pribetich (article 58 du Règlement)");
        assertKind(VoteKind.PROCEDURAL, "la demande de seconde délibération, présentée par M. Salmon, des articles 1 et 2 de la proposition");
        assertKind(VoteKind.PROCEDURAL, "la proposition du Gouvernement de prolonger la séance en cours au-delà de vingt heures");
    }

    @Test
    void other() {
        assertKind(VoteKind.OTHER, "la déclaration du Gouvernement portant sur les négociations en cours relatives à l'accord");
    }

    private void assertKind(VoteKind expected, String title) {
        assertEquals(expected, classifier.classify(classifier.normalize(title)), title);
    }

}
