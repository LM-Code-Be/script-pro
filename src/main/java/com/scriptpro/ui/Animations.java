package com.scriptpro.ui;

import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.ParallelTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.Timeline;
import javafx.animation.TranslateTransition;
import javafx.beans.property.SimpleLongProperty;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.Region;
import javafx.util.Duration;

import java.util.List;

/**
 * Animations réutilisables de l'interface : entrée des pages, cascade des cartes,
 * survol des boutons, compteurs et barres de progression.
 *
 * <p>Toutes les durées restent courtes (moins de 600 ms) : une animation doit guider
 * l'œil, jamais faire attendre l'utilisateur.</p>
 *
 * @author LM-Code
 */
public final class Animations {
    /** Courbe « ease-out » : démarre vite et ralentit, ce qui paraît plus naturel qu'un mouvement linéaire. */
    private static final Interpolator EASE_OUT = Interpolator.SPLINE(0.16, 1, 0.3, 1);

    private Animations() {
    }

    /** Fait apparaître un nœud en fondu tout en le faisant monter de quelques pixels. */
    public static void enter(Node node, Duration delay) {
        node.setOpacity(0);
        node.setTranslateY(18);
        FadeTransition fade = new FadeTransition(Duration.millis(420), node);
        fade.setToValue(1);
        TranslateTransition slide = new TranslateTransition(Duration.millis(520), node);
        slide.setToY(0);
        slide.setInterpolator(EASE_OUT);
        ParallelTransition transition = new ParallelTransition(fade, slide);
        transition.setDelay(delay);
        transition.play();
    }

    /** Fait apparaître une liste de nœuds l'un après l'autre (effet « cascade »). */
    public static void stagger(List<? extends Node> nodes) {
        for (int index = 0; index < nodes.size(); index++) {
            // Au-delà de 8 éléments, on plafonne le délai pour ne pas faire attendre l'utilisateur.
            enter(nodes.get(index), Duration.millis(60L * Math.min(index, 8)));
        }
    }

    /** Agrandit légèrement le nœud au survol et le réduit à l'appui, pour un retour visuel immédiat. */
    public static void hoverScale(Node node, double hoverScale) {
        ScaleTransition scale = new ScaleTransition(Duration.millis(160), node);
        scale.setInterpolator(EASE_OUT);
        node.setOnMouseEntered(event -> animateScale(scale, hoverScale));
        node.setOnMouseExited(event -> animateScale(scale, 1));
        node.setOnMousePressed(event -> animateScale(scale, 0.96));
        node.setOnMouseReleased(event -> animateScale(scale, node.isHover() ? hoverScale : 1));
    }

    private static void animateScale(ScaleTransition scale, double target) {
        scale.stop();
        scale.setToX(target);
        scale.setToY(target);
        scale.playFromStart();
    }

    /** Fait défiler un nombre de 0 jusqu'à sa valeur finale (indicateurs du tableau de bord). */
    public static void countUp(Label label, long target) {
        SimpleLongProperty value = new SimpleLongProperty(0);
        value.addListener((observable, oldValue, newValue) -> label.setText(String.valueOf(newValue)));
        label.setText("0");
        new Timeline(new KeyFrame(Duration.millis(900), new KeyValue(value, target, EASE_OUT))).play();
    }

    /** Remplit une barre de progression de 0 à la largeur voulue. */
    public static void growWidth(Region bar, double targetWidth, Duration delay) {
        bar.setPrefWidth(0);
        Timeline timeline = new Timeline(new KeyFrame(Duration.millis(800),
                new KeyValue(bar.prefWidthProperty(), targetWidth, EASE_OUT)));
        timeline.setDelay(delay);
        timeline.play();
    }

    /** Pulsation continue d'opacité (indicateur « en cours »). Retourne l'animation pour pouvoir l'arrêter. */
    public static FadeTransition pulse(Node node) {
        FadeTransition pulse = new FadeTransition(Duration.millis(700), node);
        pulse.setFromValue(1);
        pulse.setToValue(0.35);
        pulse.setAutoReverse(true);
        pulse.setCycleCount(FadeTransition.INDEFINITE);
        pulse.play();
        return pulse;
    }
}
