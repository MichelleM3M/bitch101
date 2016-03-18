package co.ghola.backend.service;

/**
 * Created by macbook on 3/12/16.
 */
import com.sun.syndication.feed.synd.SyndEntry;
import com.sun.syndication.feed.synd.SyndFeed;
import com.sun.syndication.io.FeedException;
import com.sun.syndication.io.SyndFeedInput;

import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Logger;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import co.ghola.backend.entity.AirQualitySample;

// [START example]
@SuppressWarnings("serial")
public class RssFetcher extends HttpServlet {

    private static AirQualitySampleWrapper api =   AirQualitySampleWrapper.getInstance();

    private static List<AirQualitySample> AirQualitySamplesInStorage = new ArrayList<AirQualitySample>();

    private static List<AirQualitySample> AirQualitySamples = new ArrayList<AirQualitySample>();

    private final static String RSS_URL ="http://www.stateair.net/dos/RSS/HoChiMinhCity/HoChiMinhCity-PM2.5.xml";

    private static DateTimeFormatter format =  DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss");

    private static final Logger log = Logger.getLogger(RssFetcher.class.getName());
    @Override
    public void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {

       // PrintWriter out = resp.getWriter();

        BufferedReader reader = null;

        //format.setTimeZone(TimeZone.getTimeZone("Asia/Bangkok"));

        try {
            URL url = new URL(RSS_URL);
            reader = new BufferedReader(new InputStreamReader(url.openStream()));
        } catch (IOException e) {
            throw new MalformedURLException();
        }

        // Reading the feed
        SyndFeedInput input = new SyndFeedInput();
        try {
            SyndFeed feed = input.build(reader);
            List entries = feed.getEntries();
            Iterator itEntries = entries.iterator();

            while (itEntries.hasNext()) {
                SyndEntry entry = (SyndEntry) itEntries.next();
                AirQualitySample sample = createSampleFromRss(entry.getDescription().getValue());
                if (sample != null){
                    AirQualitySamples.add(sample);
                }
            }
        } catch (FeedException e) {
            throw new IOException("Parsing issue, likely date related", e.getCause());
        }

        //Removing duplicates, if any

        List<AirQualitySample> AirQualitySamplesWithoutDuplicates = removeDuplicates(AirQualitySamples);

        //Persisting samples in Datastore

        AirQualitySamplesInStorage = api.getAirQualitySamples(null, 24); //retrieve last 24 hrs only

        Iterator<AirQualitySample> crunchifyIterator = AirQualitySamples.iterator();

        while (crunchifyIterator.hasNext()) {
            persistAirQualitySample(crunchifyIterator.next());
        }

    }

    private List<AirQualitySample> removeDuplicates(List<AirQualitySample> listWithDuplicates) {
    /* Set of all attributes seen so far */
        Set<DateTime> attributes = new HashSet<DateTime>();
    /* All confirmed duplicates go in here */
        List<AirQualitySample> duplicates = new ArrayList<AirQualitySample>();

        for(AirQualitySample sample : listWithDuplicates) {

            if(attributes.contains(sample.getDate())) {

                duplicates.add(sample);
            }

            attributes.add(sample.getDate());
        }

        /* Clean list without any dups */

        listWithDuplicates.removeAll(duplicates);

        return listWithDuplicates;
    }

    private  AirQualitySample createSampleFromRss(String rssStr){
        String[] arr = rssStr.split(";");
        AirQualitySample sample = null;

        sample = new AirQualitySample(arr[3].trim(), arr[4].trim(), format.parseDateTime(arr[0]));

        return sample;
    }

    public void persistAirQualitySample(AirQualitySample sample)  {

        boolean isPresent = false;

        Iterator<AirQualitySample> crunchifyIterator = AirQualitySamplesInStorage.iterator();

        while (crunchifyIterator.hasNext()) {
            AirQualitySample storedSample = (AirQualitySample)crunchifyIterator.next();
            log.info("date in Datastore:" + storedSample.getDate().toString() + " date in rss sample:" + sample.getDate().toString());
            if(storedSample.getDate().withTimeAtStartOfDay().equals(sample.getDate().withTimeAtStartOfDay())){
                log.info("present!");
                isPresent = true;
            }
            if (isPresent) break;
        }

        if(!isPresent && Integer.valueOf(sample.getAqi().trim()) != -999) {

            api.addAirQualitySample(sample);

        }
    }

}