package art.mapkluss.companion;

import java.util.Arrays;
import java.util.concurrent.*;

/** Orthographic height-field renderer, independent of Minecraft version-specific drawing APIs. */
public final class LiveBuildOrbitPreview implements AutoCloseable {
    public record Surface(int width,int depth,int[] heights,double spacing) {
        public Surface(int width,int depth,int[] heights){this(width,depth,heights,1);}
        public Surface {if(width<1||depth<1||width>1280||depth>1280||heights.length!=width*depth||!Double.isFinite(spacing)||spacing<=0)throw new IllegalArgumentException("Invalid surface");}
    }
    public record Frame(int[] pixels,long revision) { }
    public static final int WIDTH=512,HEIGHT=320;
    private final ExecutorService worker=Executors.newSingleThreadExecutor(r->{var t=new Thread(r,"mapkluss-build-orbit");t.setDaemon(true);return t;});
    private volatile Frame frame;
    private boolean busy,closed;
    private long last=-1,serial;
    private int lastAngle=-1;
    public Frame frame(){return frame;}
    public synchronized void update(Surface surface,int[] colours,long revision,int angle){
        if(closed||busy||surface==null||colours.length!=surface.width()*surface.depth()||(last==revision&&lastAngle==angle))return;
        busy=true;int[] copy=colours.clone();
        worker.submit(()->{
            try{int[] pixels=render(surface,copy,angle*Math.PI/8);
                synchronized(this){if(!closed){frame=new Frame(pixels,++serial);last=revision;lastAngle=angle;}}
            }finally{synchronized(this){busy=false;}}
        });
    }
    public static int[] render(Surface surface,int[] colours,double yaw){
        if(colours.length!=surface.heights().length||!Double.isFinite(yaw))throw new IllegalArgumentException("Invalid render input");
        int[] pixels=new int[WIDTH*HEIGHT];double[] depth=new double[pixels.length];Arrays.fill(depth,Double.NEGATIVE_INFINITY);
        int min=Integer.MAX_VALUE,max=Integer.MIN_VALUE;
        for(int i=0;i<colours.length;i++)if((colours[i]>>>24)!=0){min=Math.min(min,surface.heights()[i]);max=Math.max(max,surface.heights()[i]);}
        if(min==Integer.MAX_VALUE)return pixels;
        double scale=Math.min((WIDTH-32.0)/(Math.hypot(surface.width(),surface.depth())+2),
            (HEIGHT-32.0)/(Math.hypot(surface.width(),surface.depth())*.72+(max-min+1)/surface.spacing()*.72));
        int step=Math.max(1,(int)Math.ceil(Math.sqrt(colours.length/65536.0)));
        double cy=(min+(double)max)/2,cs=Math.cos(yaw),sn=Math.sin(yaw);
        for(int z=0;z<surface.depth();z+=step)for(int x=0;x<surface.width();x+=step){
            int i=z*surface.width()+x,colour=colours[i];if((colour>>>24)==0)continue;
            double xx=x-surface.width()/2.0,zz=z-surface.depth()/2.0,y=(surface.heights()[i]-cy)/surface.spacing();
            double[][] v=new double[8][];
            for(int n=0;n<8;n++){
                double vx=xx+((n&1)!=0?Math.min(step,surface.width()-x):0),vz=zz+((n&2)!=0?Math.min(step,surface.depth()-z):0),vy=y+((n&4)!=0?1/surface.spacing():0);
                double rx=vx*cs-vz*sn,rz=vx*sn+vz*cs;
                v[n]=new double[]{WIDTH/2.0+rx*scale,HEIGHT/2.0+(rz-vy)*.70710678*scale,(rz+vy)*.70710678};
            }
            face(pixels,depth,v,4,5,7,6,colour);
            face(pixels,depth,v,0,1,5,4,shade(colour,.66));face(pixels,depth,v,2,3,7,6,shade(colour,.66));
            face(pixels,depth,v,0,2,6,4,shade(colour,.8));face(pixels,depth,v,1,3,7,5,shade(colour,.8));
        }
        return pixels;
    }
    private static int shade(int colour,double value){return (colour&0xff000000)|((int)(((colour>>>16)&255)*value)<<16)|((int)(((colour>>>8)&255)*value)<<8)|(int)((colour&255)*value);}
    private static void face(int[] out,double[] depth,double[][] v,int a,int b,int c,int d,int colour){triangle(out,depth,v[a],v[b],v[c],colour);triangle(out,depth,v[a],v[c],v[d],colour);}
    private static void triangle(int[] out,double[] depth,double[] a,double[] b,double[] c,int colour){
        double det=(b[1]-c[1])*(a[0]-c[0])+(c[0]-b[0])*(a[1]-c[1]);if(Math.abs(det)<1e-8)return;
        int left=Math.max(0,(int)Math.floor(Math.min(a[0],Math.min(b[0],c[0])))),right=Math.min(WIDTH-1,(int)Math.ceil(Math.max(a[0],Math.max(b[0],c[0]))));
        int top=Math.max(0,(int)Math.floor(Math.min(a[1],Math.min(b[1],c[1])))),bottom=Math.min(HEIGHT-1,(int)Math.ceil(Math.max(a[1],Math.max(b[1],c[1]))));
        for(int y=top;y<=bottom;y++)for(int x=left;x<=right;x++){
            double u=((b[1]-c[1])*(x+.5-c[0])+(c[0]-b[0])*(y+.5-c[1]))/det;
            double v=((c[1]-a[1])*(x+.5-c[0])+(a[0]-c[0])*(y+.5-c[1]))/det,w=1-u-v;
            if(u<0||v<0||w<0)continue;double distance=u*a[2]+v*b[2]+w*c[2];int index=y*WIDTH+x;
            if(distance>=depth[index]){depth[index]=distance;out[index]=colour;}
        }
    }
    @Override public synchronized void close(){closed=true;worker.shutdownNow();frame=null;}
}
