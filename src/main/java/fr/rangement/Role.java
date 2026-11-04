package fr.rangement;

/** Ce que fait un coffre pour le plugin. */
enum Role {
    /** Coffre de tri : son contenu est envoye dans les autres coffres. */
    TRI,
    /** Coffre divers : recoit ce qui n'a pas trouve de coffre. */
    DIVERS,
    /** Coffre de rangement ordinaire. */
    NORMAL
}
