/**
 * This file is hereby placed into the Public Domain. This means anyone is
 * free to do whatever they wish with this file.
 */
package mil.nga.giat.process.elasticsearch;

import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;

import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.feature.DefaultFeatureCollection;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.referencing.CRS;
import org.geotools.referencing.crs.DefaultGeographicCRS;
import org.junit.Before;
import org.junit.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.davidmoten.geo.GeoHash;
import com.github.davidmoten.geo.LatLong;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.vividsolutions.jts.geom.Envelope;

public class GeoHashGridTest {

    private static final String BUCKET_NAME = "bucket1";
    private static final int DOC_COUNT = 11;
    private static final int VALUE = 25;
    private static final String METRIC_KEY = "metric_key";
    private static final String VALUE_KEY = "value_key";
    private static final String AGG_KEY = "nested_agg";
    private static final int[] AGG_RESULTS = {1, 2, 3, 4, 5};
    private static final Map<String,Object> SIMPLE_BUCKET = TestUtil.createDocCountBucket(BUCKET_NAME, DOC_COUNT);
    private static final Map<String,Object> METRIC_BUCKET = TestUtil.createMetricBucket(DOC_COUNT, METRIC_KEY, VALUE_KEY, VALUE);
    private static final Map<String,Object> AGG_BUCKET = TestUtil.createAggBucket(AGG_KEY, AGG_RESULTS);
    
    private SimpleFeatureCollection features;

    private GeoHashGrid geohashGrid;

    private ObjectMapper mapper;

    @Before
    public void setup() {
        this.geohashGrid = new BasicGeoHashGrid();
        this.mapper = new ObjectMapper();
    }

    @Test
    public void testGeoHashGrid() throws Exception {
        features = TestUtil.createAggregationFeatures(ImmutableList.of(
                ImmutableMap.of("_aggregation", mapper.writeValueAsBytes(ImmutableMap.of("key",GeoHash.encodeHash(new LatLong(-89.9,-179.9),1),"doc_count",10)))
                ));
        ReferencedEnvelope envelope = new ReferencedEnvelope(-180,180,-90,90,DefaultGeographicCRS.WGS84);
        geohashGrid.initalize(envelope, features);
        assertEquals(GeoHash.widthDegrees(1), geohashGrid.getCellWidth(), 1e-10);
        assertEquals(GeoHash.heightDegrees(1), geohashGrid.getCellHeight(), 1e-10);
        assertEquals(envelope, geohashGrid.getBoundingBox());
        assertEquals(new Envelope(-180+GeoHash.widthDegrees(1)/2.,180-GeoHash.widthDegrees(1)/2.,-90+GeoHash.heightDegrees(1)/2.,90-GeoHash.heightDegrees(1)/2.), geohashGrid.getEnvelope());
        int ny = (int) Math.round(180/geohashGrid.getCellHeight());
        int nx = (int) Math.round(360/GeoHash.widthDegrees(1));
        float[][] expected = new float[ny][nx];
        expected[ny-1][0] = 10;
        assertEquals(ny, geohashGrid.getGrid().length);
        assertEquals(nx, geohashGrid.getGrid()[0].length);
        IntStream.range(0, ny).forEach(i->assertTrue(Arrays.equals(expected[i], geohashGrid.getGrid()[i])));
    }

    @Test
    public void testGeoHashGridWithProjectedEnvelope() throws Exception {
        features = TestUtil.createAggregationFeatures(ImmutableList.of(
                ImmutableMap.of("_aggregation", mapper.writeValueAsBytes(ImmutableMap.of("key",GeoHash.encodeHash(new LatLong(-89.9,-179.9),1),"doc_count",10)))
                ));
        ReferencedEnvelope envelope = new ReferencedEnvelope(-19926188.85,19926188.85,-30240971.96,30240971.96, CRS.decode("EPSG:3857"));
        geohashGrid.initalize(envelope, features);

        assertEquals(new ReferencedEnvelope(-180,180,-90,90,DefaultGeographicCRS.WGS84), geohashGrid.getBoundingBox());
    }

    @Test
    public void testGeoHashGridWithNoFeatures() throws Exception {
        features = new DefaultFeatureCollection();
        ReferencedEnvelope envelope = new ReferencedEnvelope(-180,180,-90,90,CRS.decode("EPSG:4326"));
        geohashGrid.initalize(envelope, features);
        IntStream.range(0, geohashGrid.getGrid().length).forEach(i->assertTrue(Arrays.equals(new float[geohashGrid.getGrid()[i].length], geohashGrid.getGrid()[i])));
    }

    @Test
    public void testGeoHashGridWithNoAggregations() throws Exception {
        features = TestUtil.createAggregationFeatures(ImmutableList.of(
                ImmutableMap.of("aString", UUID.randomUUID().toString())
                ));
        ReferencedEnvelope envelope = new ReferencedEnvelope(-180,180,-90,90,CRS.decode("EPSG:4326"));
        geohashGrid.initalize(envelope, features);
        IntStream.range(0, geohashGrid.getGrid().length).forEach(i->assertTrue(Arrays.equals(new float[geohashGrid.getGrid()[i].length], geohashGrid.getGrid()[i])));
    }

    @Test(expected=IllegalArgumentException.class)
    public void testGeoHashGridWithNoDocCount() throws Exception {
        features = TestUtil.createAggregationFeatures(ImmutableList.of(
                ImmutableMap.of("_aggregation", mapper.writeValueAsBytes(ImmutableMap.of("key",GeoHash.encodeHash(new LatLong(-89.9,-179.9),1))))
                ));
        ReferencedEnvelope envelope = new ReferencedEnvelope(-180,180,-90,90,CRS.decode("EPSG:4326"));
        geohashGrid.initalize(envelope, features);
        IntStream.range(0, geohashGrid.getGrid().length).forEach(i->assertTrue(Arrays.equals(new float[geohashGrid.getGrid()[i].length], geohashGrid.getGrid()[i])));
    }

    @Test
    public void testGeoHashGridWithInvalidGeohash() throws Exception {
        features = TestUtil.createAggregationFeatures(ImmutableList.of(
                ImmutableMap.of("_aggregation", mapper.writeValueAsBytes(ImmutableMap.of("key","invalid","doc_count",10)))
                ));
        ReferencedEnvelope envelope = new ReferencedEnvelope(-180,180,-90,90,CRS.decode("EPSG:4326"));
        geohashGrid.initalize(envelope, features);
        IntStream.range(0, geohashGrid.getGrid().length).forEach(i->assertTrue(Arrays.equals(new float[geohashGrid.getGrid()[i].length], geohashGrid.getGrid()[i])));
    }
    
    @Test
    public void testPluckBucketName() {
        String plucked = this.geohashGrid.pluckBucketName(SIMPLE_BUCKET);
        assertEquals(BUCKET_NAME, plucked);
    }
    
    @Test
    public void testPluckDocCount() {
        Number plucked = this.geohashGrid.pluckDocCount(SIMPLE_BUCKET);
        assertEquals(DOC_COUNT, plucked);
    }
    
    @Test
    public void testPluckMetricValue() {
        Number plucked = this.geohashGrid.pluckMetricValue(METRIC_BUCKET, METRIC_KEY, VALUE_KEY);
        assertEquals(VALUE, plucked);
    }
    
    @Test
    public void testPluckMetricValue_docCount() {
        Number plucked = this.geohashGrid.pluckMetricValue(METRIC_BUCKET, null, null);
        assertEquals(DOC_COUNT, plucked);
    }
    
    @Test(expected=IllegalArgumentException.class)
    public void testPluckMetricValue_canNotFindMetricKey() {
        this.geohashGrid.pluckMetricValue(METRIC_BUCKET, "noGonnaFindMe", VALUE_KEY);
    }
    
    @Test(expected=IllegalArgumentException.class)
    public void testPluckMetricValue_canNotFindValueKey() {
        this.geohashGrid.pluckMetricValue(METRIC_BUCKET, METRIC_KEY, "noGonnaFindMe");
    }
    
    @Test
    public void testPluckAggBuckets() {
        List<Map<String,Object>> buckets = this.geohashGrid.pluckAggBuckets(AGG_BUCKET, AGG_KEY);
        assertEquals(AGG_RESULTS.length, buckets.size());
    }
    
    @Test(expected=IllegalArgumentException.class)
    public void testPluckAggBuckets_canNotFindAggKey() {
        this.geohashGrid.pluckAggBuckets(AGG_BUCKET, "noGonnaFindMe");
    }
}
