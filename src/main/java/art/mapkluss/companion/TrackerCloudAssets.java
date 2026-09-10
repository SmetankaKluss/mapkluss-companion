package art.mapkluss.companion;

import java.io.IOException;

/** Resolve pinned Cloud assets afresh; persisted signed links are not durable identifiers. */
final class TrackerCloudAssets {
    record LinkedSource(LiveBuildSourceCache.Loaded loaded, String artId, String versionId) { }
    static LinkedSource linkedSource(CompanionApiClient api,LiveBuildSourceCache cache,String artId,String versionId)throws Exception {
        var manifest=api.manifest(artId,versionId);
        requireVersion(manifest,artId,versionId);
        return new LinkedSource(source(api,cache,manifest),manifest.artId(),manifest.versionId());
    }
    static String preview(CompanionApiClient api,BuildSessionState session)throws Exception {
        if(session.art_id()==null||session.art_id().isBlank())return session.image_preview();
        var manifest=api.manifest(session.art_id(),session.art_version_id());
        requireVersion(manifest,session.art_id(),session.art_version_id());
        if(manifest.previewUrl()==null||manifest.previewUrl().isBlank())throw new IOException("Preview missing");
        return manifest.previewUrl();
    }

    static LiveBuildSourceCache.Loaded source(CompanionApiClient api,LiveBuildSourceCache cache,
        String artId,String versionId)throws Exception {
        var manifest=api.manifest(artId,versionId);
        requireVersion(manifest,artId,versionId);
        return source(api,cache,manifest);
    }
    private static LiveBuildSourceCache.Loaded source(CompanionApiClient api,LiveBuildSourceCache cache,CompanionManifest manifest)throws Exception {
        // Follow the same source as Library's Install/Place. A Cloud art may also
        // provide an optional Two-layer export without changing its ordinary scheme.
        boolean layered=LibraryPlacementSource.twoLayer(manifest)
            ||(manifest.litematicArtifact().isEmpty()&&manifest.hasSuppressionBundle());
        if(layered&&manifest.suppressionBundleArtifact().isEmpty()){
            // Older single-map Cloud arts store the pinned plan and schematic separately.
            var grid=manifest.grid();
            if(grid==null||grid.wide()!=1||grid.tall()!=1)throw new IOException("Full Two-layer bundle required");
            var catalog=SuppressionBundleService.downloadCatalog(api,manifest);
            return cache.importCatalog(catalog);
        }
        var artifact=(layered?manifest.suppressionBundleArtifact():manifest.litematicArtifact())
            .orElseThrow(()->new IOException("Build source missing"));
        var kind=layered?LiveBuildSessionStore.SourceKind.TWO_LAYER_ZIP:LiveBuildSessionStore.SourceKind.LITEMATIC;
        var reference=new LiveBuildSourceCache.Reference(kind,artifact.sha256());
        try{return cache.load(reference);}catch(IOException unavailable){ /* Fetch a validated replacement below. */ }
        int limit=layered?SuppressionPlanParser.MAX_BUNDLE_BYTES:LiveBuildSchematic.MAX_FILE_BYTES;
        byte[] bytes=api.downloadArtifactBounded(artifact,limit);
        if(bytes.length!=artifact.sizeBytes()||!SuppressionHashes.sha256(bytes).equals(reference.sha256()))
            throw new IOException("Build source checksum mismatch");
        return cache.importOwnedBytes(bytes,kind);
    }
    private static void requireVersion(CompanionManifest manifest,String art,String version)throws IOException {
        if(manifest==null||!art.equals(manifest.artId())||(version!=null&&!version.isBlank()&&!version.equals(manifest.versionId())))
            throw new IOException("Cloud version mismatch");
    }
}
