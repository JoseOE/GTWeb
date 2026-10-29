package com.gymtrack.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

// Un tag RFID (tarjeta o sticker UHF) ligado a un socio. El EPC es el número
// que lee el YRM, en hexadecimal y mayúsculas.
@Document(collection = "tarjetas")
public class Tarjeta {

    @Id
    private String id;
    private String gymId;
    @Indexed(unique = true)
    private String epc;
    private String userId;
    private Instant creadaEn;

    public Tarjeta() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getGymId() { return gymId; }
    public void setGymId(String gymId) { this.gymId = gymId; }

    public String getEpc() { return epc; }
    public void setEpc(String epc) { this.epc = epc; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public Instant getCreadaEn() { return creadaEn; }
    public void setCreadaEn(Instant creadaEn) { this.creadaEn = creadaEn; }
}
