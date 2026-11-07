package com.limasegura.limasegurabackend.model;

public enum IncidentType {
    ROBBERY,
    ARMED_ROBBERY,
    VEHICLE_THEFT,
    THEFT,
    EXTORTION,
    HOMICIDE,
    KIDNAPPING,
    ASSAULT,
    FRAUD,
    DRUG_TRAFFICKING,
    SEXUAL_ASSAULT,
    VANDALISM,
    SHOOTING,
    HIT_AND_RUN,
    FEMICIDE,
    OTHER;

    /**
     * Devuelve el nivel de gravedad del delito (rango 1 a 10) según el
     * Informe Técnico de Ingesta y Ponderación para Lima Segura:
     * - Homicidio / Sicariato / Feminicidio: 10
     * - Secuestro / Balacera: 9
     * - Extorsión / Violación: 8
     * - Robo a mano armada / Asalto: 7
     * - Narcotráfico / Atropello y fuga: 6
     * - Hurto simple / Estafa: 4
     * - Vandalismo / Otros: 3
     */
    public int getSeverity() {
        return switch (this) {
            case HOMICIDE, FEMICIDE -> 10;
            case KIDNAPPING, SHOOTING -> 9;
            case EXTORTION, SEXUAL_ASSAULT -> 8;
            case ROBBERY, ARMED_ROBBERY, ASSAULT -> 7;
            case VEHICLE_THEFT, DRUG_TRAFFICKING, HIT_AND_RUN -> 6;
            case THEFT, FRAUD -> 4;
            case VANDALISM, OTHER -> 3;
        };
    }
}
