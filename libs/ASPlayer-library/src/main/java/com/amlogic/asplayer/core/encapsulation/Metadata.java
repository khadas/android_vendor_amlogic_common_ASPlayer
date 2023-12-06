package com.amlogic.asplayer.core.encapsulation;

public abstract class Metadata {
    public static class TunerMetadata extends Metadata {
        public static final int TYPE_MAIN = 0;
        public static final int TYPE_SUPPLEMENTARY = 1;
        public final int audioType;
        public int encodingType;
        public int filterId;

        public TunerMetadata(int audioType, int encodingType, int filterId) {
            this.audioType = audioType;
            this.encodingType = encodingType;
            this.filterId = filterId;
        }

        @Override
        public TunerMetadata clone() {
            TunerMetadata metadata = new TunerMetadata(this.audioType, this.encodingType, this.filterId);
            return metadata;
        }

        @Override
        public String toString() {
            return "TunerMetadata{" +
                    "audioType=" + audioType +
                    ", encodingType=" + encodingType +
                    ", filterId=" + filterId +
                    '}';
        }
    }

    public static class PlacementMetadata extends Metadata {
        public static final int PLACEMENT_NORMAL = 0;
        public static final int PLACEMENT_RIGHT = 1;
        public static final int PLACEMENT_LEFT = 2;
        public int placement;
        public PlacementMetadata(int placement) {
            this.placement = placement;
        }

        @Override
        public PlacementMetadata clone() {
            PlacementMetadata metadata = new PlacementMetadata(this.placement);
            return metadata;
        }

        @Override
        public String toString() {
            return "PlacementMetadata{" +
                    "placement=" + placement +
                    '}';
        }
    }
}
