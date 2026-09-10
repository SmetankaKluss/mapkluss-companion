import java.awt.Color;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;

/** Original, integer-grid sprites. White masks are tinted with semantic colors. */
class GenerateWorkshopSprites {
    static final String[] NAMES={"library","lens","scan","tracker","account","search","refresh","install","download","edit","link","back","more","delete","close","favorite","layers","check","folder","pause"};
    public static void main(String[] args) throws Exception {
        Path out=Path.of("src/main/resources/assets/mapkluss-companion/textures/gui/workshop");
        Files.createDirectories(out);
        var atlas=new BufferedImage(NAMES.length*16,16,BufferedImage.TYPE_INT_ARGB);
        for(int i=0;i<NAMES.length;i++) {
            var g=atlas.createGraphics(); g.translate(i*16,0);g.setColor(Color.WHITE);
            switch(NAMES[i]) {
                case "library" -> {g.drawRect(2,3,5,10);g.drawRect(9,3,4,10);g.drawLine(4,5,5,5);}
                case "lens","search" -> {g.drawOval(2,2,8,8);g.drawLine(10,10,14,14);}
                case "scan" -> {g.drawPolyline(new int[]{2,2,5},new int[]{5,2,2},3);g.drawPolyline(new int[]{10,13,13},new int[]{2,2,5},3);g.drawPolyline(new int[]{2,2,5},new int[]{10,13,13},3);g.drawPolyline(new int[]{10,13,13},new int[]{13,13,10},3);g.drawLine(5,7,10,7);}
                case "tracker" -> {g.drawRect(2,6,3,3);g.drawRect(10,2,3,3);g.drawRect(10,10,3,3);g.drawLine(5,7,10,4);g.drawLine(5,8,10,11);}
                case "account" -> {g.drawOval(5,2,5,5);g.drawPolyline(new int[]{2,2,5,10,13,13,2},new int[]{13,10,8,8,10,13,13},7);}
                case "refresh" -> {g.drawArc(2,2,11,11,40,260);g.drawPolyline(new int[]{9,13,13},new int[]{4,4,0},3);}
                case "install","download" -> {g.drawLine(7,2,7,10);g.drawPolyline(new int[]{4,7,10},new int[]{7,10,7},3);g.drawPolyline(new int[]{2,2,13,13},new int[]{10,13,13,10},4);}
                case "edit" -> {g.drawPolygon(new int[]{2,3,11,14,6},new int[]{13,9,1,4,12},5);g.drawLine(9,3,12,6);}
                case "link" -> {g.drawRect(2,6,7,7);g.drawPolyline(new int[]{7,13,13},new int[]{2,2,8},3);g.drawLine(6,9,13,2);}
                case "back" -> {g.drawLine(2,7,13,7);g.drawPolyline(new int[]{6,2,6},new int[]{3,7,11},3);}
                case "more" -> {for(int x:new int[]{2,7,12})g.fillRect(x,7,2,2);}
                case "delete" -> {g.drawLine(2,4,13,4);g.drawRect(5,2,5,2);g.drawRect(4,5,7,8);g.drawLine(6,7,6,11);g.drawLine(9,7,9,11);}
                case "close" -> {g.drawLine(3,3,12,12);g.drawLine(3,12,12,3);}
                case "favorite" -> g.drawPolygon(new int[]{7,9,14,10,12,7,2,4,0,5},new int[]{1,5,5,8,13,10,13,8,5,5},10);
                case "layers" -> {g.drawPolygon(new int[]{1,7,14,7},new int[]{5,2,5,8},4);g.drawPolyline(new int[]{1,7,14},new int[]{8,11,8},3);g.drawPolyline(new int[]{1,7,14},new int[]{11,14,11},3);}
                case "check" -> g.drawPolyline(new int[]{2,6,13},new int[]{7,11,3},3);
                case "folder" -> g.drawPolygon(new int[]{1,1,6,8,14,14},new int[]{12,3,3,5,5,12},6);
                case "pause" -> {g.fillRect(4,3,2,10);g.fillRect(10,3,2,10);}
            }
            g.dispose();
        }
        ImageIO.write(atlas,"png",out.resolve("icons.png").toFile());
        for(String kind:new String[]{"frame","button"}) {
            var mask=new BufferedImage(12,12,BufferedImage.TYPE_INT_ARGB);var g=mask.createGraphics();
            g.setColor(new Color(0xff888888,true));g.fillRect(1,0,10,12);g.fillRect(0,1,12,10);
            g.setColor(Color.WHITE);g.drawLine(1,0,10,0);g.drawLine(0,1,0,10);
            g.setColor(new Color(0xff333333,true));g.drawLine(1,11,10,11);g.drawLine(11,1,11,10);
            g.setColor(new Color(0xffaaaaaa,true));g.fillRect(2,2,8,8);
            if(kind.equals("frame")){g.setColor(new Color(0xff444444,true));g.drawRect(3,3,5,5);}
            g.dispose();ImageIO.write(mask,"png",out.resolve(kind+".png").toFile());
        }
        System.out.println("Generated 20 original 16px icons and two 12px nine-slice masks.");
    }
}
