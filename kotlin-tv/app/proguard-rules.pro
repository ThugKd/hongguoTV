-dontwarn org.bouncycastle.**
-dontwarn javax.naming.**
-dontwarn javax.mail.**
-dontwarn javax.activation.**

# Media3 resolves the video effects graph and frame processor by reflection.
-keep class androidx.media3.effect.SingleInputVideoGraph$Factory {
    public <init>(androidx.media3.common.VideoFrameProcessor$Factory);
}
-keep class androidx.media3.effect.DefaultVideoFrameProcessor$Factory$Builder {
    public <init>();
    public androidx.media3.effect.DefaultVideoFrameProcessor$Factory$Builder setEnableReplayableCache(boolean);
    public androidx.media3.effect.DefaultVideoFrameProcessor$Factory build();
}
