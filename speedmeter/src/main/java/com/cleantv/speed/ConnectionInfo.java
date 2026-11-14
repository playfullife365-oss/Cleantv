package com.cleantv.speed;

import java.util.Objects;

/** How the TV was connected: the kind of network and, on Wi-Fi, its band, generation, signal and link rate. */
final class ConnectionInfo {
    static final int NONE = 0;
    static final int WIFI = 1;
    static final int ETHERNET = 2;
    static final int OTHER = 3;

    final int kind;
    /** "5 GHz" and the like, or null. */
    final String band;
    /** "Wi-Fi 6" and the like, or null. */
    final String generation;
    /** Signal in dBm, or 0 when unknown. Real readings are always negative. */
    final int rssi;
    /** Wi-Fi link rate to the router in Mbps, or 0 when unknown. */
    final int linkMbps;

    ConnectionInfo(int kind, String band, String generation, int rssi, int linkMbps) {
        this.kind = kind;
        this.band = band;
        this.generation = generation;
        this.rssi = rssi;
        this.linkMbps = linkMbps;
    }

    /** Groups tests over the same kind of connection: same network type, band and generation. */
    String key() {
        return kind + "|" + (band == null ? "" : band) + "|" + (generation == null ? "" : generation);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof ConnectionInfo)) return false;
        ConnectionInfo other = (ConnectionInfo) o;
        return kind == other.kind && rssi == other.rssi && linkMbps == other.linkMbps
                && Objects.equals(band, other.band) && Objects.equals(generation, other.generation);
    }

    @Override
    public int hashCode() {
        return Objects.hash(kind, band, generation, rssi, linkMbps);
    }
}
