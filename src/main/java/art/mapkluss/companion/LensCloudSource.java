package art.mapkluss.companion;

import com.google.gson.Gson;
import java.nio.file.*;
import java.io.*;
import java.net.URI;
import java.util.*;
import javax.imageio.ImageIO;

/** Worker-only preparation and local editor links. No publisher credentials are persisted. */
final class LensCloudSource {
    record Link(String art,String version) {
        Link { UUID.fromString(art);UUID.fromString(version); }
    }
    static byte[] preview(CompanionApiClient api,CompanionManifest manifest)throws Exception {
        var artifact=manifest.artifacts().stream().filter(a->"preview_png".equals(a.kind())).findFirst().orElseThrow(()->new IOException("Preview missing"));
        var bytes=api.downloadArtifactBounded(artifact,8*1024*1024);
        if(bytes.length!=artifact.sizeBytes()||!SuppressionHashes.sha256(bytes).equalsIgnoreCase(artifact.sha256()))throw new IOException("Preview changed");
        try(var input=ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))){
            var readers=ImageIO.getImageReaders(input);
            if(!readers.hasNext())throw new IOException("Invalid preview");
            var reader=readers.next();
            try {
                reader.setInput(input);
                int w=reader.getWidth(0),h=reader.getHeight(0);
                if(w<1||h<1||(long)w*h>16777216)throw new IOException("Preview too large");
                int width=manifest.grid().wide()*128,height=manifest.grid().tall()*128;
                if(width<128||height<128||width>1280||height>1280)throw new IOException("Invalid map grid");
                var image=reader.read(0);
                var output=new java.awt.image.BufferedImage(width,height,java.awt.image.BufferedImage.TYPE_INT_ARGB);
                for(int y=0;y<height;y++)for(int x=0;x<width;x++)output.setRGB(x,y,image.getRGB(x*w/width,y*h/height));
                var png=new ByteArrayOutputStream();ImageIO.write(output,"png",png);
                if(png.size()>2*1024*1024)throw new IOException("Lens preview too large");
                return png.toByteArray();
            } finally { reader.dispose(); }
        }
    }
    static Path folder(Path run){return run.resolve("config/mapkluss-companion/lens-cloud");}
    static void save(Path run,String session,CompanionManifest manifest)throws IOException {
        UUID.fromString(session);Files.createDirectories(folder(run));
        AtomicFiles.write(folder(run).resolve(session+".json"),new Gson().toJson(new Link(manifest.artId(),manifest.versionId())).getBytes(java.nio.charset.StandardCharsets.UTF_8),true);
    }
    static URI editor(CompanionConfig config,Path run,String session)throws IOException {
        UUID.fromString(session);var file=folder(run).resolve(session+".json");
        if(Files.size(file)>1024)throw new IOException("Invalid Lens link");
        var link=new Gson().fromJson(Files.readString(file),Link.class);
        return config.siteUri("/?art="+link.art()+"&artVersion="+link.version()+"&lensSession="+session);
    }
}
