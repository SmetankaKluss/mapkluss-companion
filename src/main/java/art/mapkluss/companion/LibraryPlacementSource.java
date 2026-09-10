package art.mapkluss.companion;

import java.io.*;
import java.nio.file.*;
import java.util.zip.*;

/** Worker-only pinned source preparation; never changes the world or an active workflow. */
final class LibraryPlacementSource {
    record Prepared(Path path, String sha256, String planSha256, CompanionManifest manifest,
                    LiveBuildProgress.Position offset) {
        boolean twoLayer() { return planSha256 != null; }
        LiveBuildProgress.Position schematicOrigin(int x,int y,int z) {
            return new LiveBuildProgress.Position(Math.subtractExact(x,offset.x()),
                Math.subtractExact(y,offset.y()),Math.subtractExact(z,offset.z()));
        }
    }
    static int count(CompanionManifest.Grid grid) {
        if(grid==null||grid.wide()<1||grid.tall()<1||grid.wide()>10||grid.tall()>10)
            throw new IllegalArgumentException("Invalid placement grid");
        return grid.wide()*grid.tall();
    }
    static boolean twoLayer(CompanionManifest manifest) {
        return "suppression_two_layer".equals(manifest.buildTechnique())||"two-layer".equals(manifest.buildTechnique());
    }
    static Prepared prepare(CompanionApiClient api,Path runDir,String art,String version,int tile)throws Exception {
        var manifest=api.manifest(art,version);
        if(!art.equals(manifest.artId())||(version!=null&&!version.isBlank()&&!version.equals(manifest.versionId())))
            throw new IOException("Placement version changed");
        int count=count(manifest.grid());
        if(tile<0||tile>=count)throw new IOException("Invalid selected map");
        if(twoLayer(manifest)) {
            var catalog=SuppressionBundleService.downloadCatalog(api,manifest);
            if(catalog.gridWide()!=manifest.grid().wide()||catalog.gridTall()!=manifest.grid().tall())
                throw new IOException("Two-layer grid changed");
            var part=catalog.tiles().stream().filter(t->t.row()*catalog.gridWide()+t.column()==tile).findFirst().orElseThrow();
            var installed=SuppressionBundleInstaller.installCatalog(runDir,catalog,catalog.tiles().indexOf(part));
            return new Prepared(installed.schematicPath(),part.bundle().litematicSha256(),part.bundle().planSha256(),manifest,
                new LiveBuildProgress.Position(0,0,0));
        }
        var artifact=(count==1?manifest.litematicArtifact():manifest.litematicTilesArtifact())
            .orElseThrow(()->new IOException("Selected map source missing"));
        byte[] bytes=api.downloadArtifactBounded(artifact,count==1?LiveBuildSchematic.MAX_FILE_BYTES:64*1024*1024);
        if(bytes.length!=artifact.sizeBytes()||!SuppressionHashes.sha256(bytes).equalsIgnoreCase(artifact.sha256()))
            throw new IOException("Placement source checksum mismatch");
        if(count>1)bytes=selectTile(bytes,manifest.grid(),tile);
        var bounds=LiveBuildSchematic.read(bytes).artBounds();
        String sha=SuppressionHashes.sha256(bytes);
        Path folder=LitematicaPaths.defaultSchematicDir(runDir).resolve("mapkluss-placement");
        Files.createDirectories(folder);
        Path target=folder.resolve(sha+".litematic");
        if(Files.exists(target)){
            if(!SuppressionHashes.sha256(target,LiveBuildSchematic.MAX_FILE_BYTES).equals(sha))
                throw new IOException("Existing placement source changed");
        }else AtomicFiles.write(target,bytes,false);
        return new Prepared(target,sha,null,manifest,new LiveBuildProgress.Position(bounds.minX(),bounds.minY(),bounds.minZ()));
    }
    static byte[] selectTile(byte[] bytes,CompanionManifest.Grid grid,int tile)throws IOException {
        if(tile<0||tile>=count(grid))throw new IOException("Invalid selected map");
        String numbered="mapart_"+(tile+1);
        String exact=numbered+"_"+(tile%grid.wide()+1)+"x"+(tile/grid.wide()+1)+".litematic";
        byte[] selected=null;long expanded=0;int entries=0;
        try(var zip=new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while((entry=zip.getNextEntry())!=null) {
                if(++entries>512)throw new IOException("Too many ZIP entries");
                if(entry.isDirectory())continue;
                byte[] payload=CompanionApiClient.readBounded(zip,LiveBuildSchematic.MAX_FILE_BYTES);
                expanded+=payload.length;if(expanded>128L*1024*1024)throw new IOException("ZIP too large");
                if(!entry.getName().equals(exact)&&!entry.getName().equals(numbered+".litematic"))continue;
                if(selected!=null)throw new IOException("Duplicate selected map");
                selected=payload;
            }
        }
        if(selected==null)throw new IOException("Selected map not in ZIP");
        return selected;
    }
}
