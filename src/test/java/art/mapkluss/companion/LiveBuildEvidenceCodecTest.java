package art.mapkluss.companion;

import org.junit.jupiter.api.Test;
import java.util.Arrays;
import java.util.Base64;
import static org.junit.jupiter.api.Assertions.*;

class LiveBuildEvidenceCodecTest {
    @Test void uploadMatchesSqlBigEndianWordsAndRoundsBackwards() {
        String packed = LiveBuildEvidenceCodec.upload(new byte[]{1,2,3,0},new long[]{1000,999,1100,0},1000,1200);
        assertArrayEquals(new byte[]{0x25,(byte)0x81,0x25,0x7a,0x25,(byte)0x8b,0x25,(byte)0x80},Base64.getDecoder().decode(packed));
    }
    @Test void downloadIncludesTransitAndExpiresWithoutRefresh() {
        var page = LiveBuildEvidenceCodec.download("ACk=",1,1000);
        assertEquals(500,page.observedAt(0));
        assertEquals(1,page.state(0,2000));
        assertEquals(1,page.state(0,120500));
        assertEquals(4,page.state(0,120501));
        assertEquals(0,page.state(0,999));
        assertEquals(4,LiveBuildEvidenceCodec.download("JYw=",1,2000).state(0,2000));
    }
    @Test void unknownAndStaleNeverUploadAsCorrect() {
        var raw=Base64.getDecoder().decode(LiveBuildEvidenceCodec.upload(new byte[]{1,4,0},new long[]{0,999999,999999},120001,120001));
        for(int i=1;i<raw.length;i+=2) assertEquals(0,raw[i]&7);
    }
    @Test void validatesSizesValuesAndCanonicalBase64() {
        for(String value : new String[]{"AA", "AA==", "AAQ= ", "AAU=", "////", "JZA="})
            assertThrows(IllegalArgumentException.class,()->LiveBuildEvidenceCodec.download(value,1,1000));
        assertThrows(IllegalArgumentException.class,()->LiveBuildEvidenceCodec.download("AAA=",2,1000));
        assertThrows(IllegalArgumentException.class,()->LiveBuildEvidenceCodec.upload(new byte[]{1},new long[]{1001},1000,1000));
        assertThrows(IllegalArgumentException.class,()->LiveBuildEvidenceCodec.upload(new byte[]{5},new long[]{0},1000,1000));
        assertThrows(IllegalArgumentException.class,()->LiveBuildEvidenceCodec.upload(new byte[]{1},new long[]{1000},1000,31001));
    }
    @Test void fullPageHasBoundedSizeAndIsDeterministic() {
        byte[] states=new byte[4096];Arrays.fill(states,(byte)1);
        long[] times=new long[4096];Arrays.fill(times,1000);
        String packed=LiveBuildEvidenceCodec.upload(states,times,1000,1100);
        assertEquals(10924,packed.length());
        assertEquals(packed,LiveBuildEvidenceCodec.upload(states,times,1000,1100));
        assertThrows(IllegalArgumentException.class,()->LiveBuildEvidenceCodec.upload(new byte[4097],new long[4097],1000,1000));
    }
    @Test void leaseRenewalAndLogoutRejectQueuedTickets() {
        var lease=new LiveBuildPublishingLease();
        String nonce="11111111-1111-4111-8111-111111111111";
        lease.accept(nonce,1000,2000);
        var first=lease.next(2000);
        assertEquals(1,first.sequence()); assertEquals(2000,first.sampleOrigin());
        assertEquals(2,lease.next(2000).sequence());
        assertTrue(lease.current(first,30000)); assertFalse(lease.current(first,31000));
        assertThrows(IllegalStateException.class,()->lease.next(31000));
        lease.accept(nonce,40000,41000);
        assertFalse(lease.current(first,41000));
        var renewed=lease.next(41000);assertEquals(1,renewed.sequence());
        lease.clear();assertFalse(lease.current(renewed,41000));
        assertThrows(IllegalArgumentException.class,()->lease.accept(nonce,0,30000));
    }
}
