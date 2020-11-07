import com.github.kiulian.downloader.YoutubeDownloader;
import com.github.kiulian.downloader.YoutubeException;
import com.github.kiulian.downloader.model.VideoDetails;
import com.github.kiulian.downloader.model.YoutubeVideo;
import com.github.kiulian.downloader.model.formats.AudioVideoFormat;
import com.github.kiulian.downloader.model.formats.Format;
import com.github.kiulian.downloader.model.formats.VideoFormat;
import com.github.kiulian.downloader.model.quality.VideoQuality;
import net.bramp.ffmpeg.FFmpeg;
import net.bramp.ffmpeg.FFmpegExecutor;
import net.bramp.ffmpeg.FFmpegUtils;
import net.bramp.ffmpeg.FFprobe;
import net.bramp.ffmpeg.builder.FFmpegBuilder;
import net.bramp.ffmpeg.job.FFmpegJob;
import net.bramp.ffmpeg.probe.FFmpegProbeResult;
import net.bramp.ffmpeg.progress.Progress;
import net.bramp.ffmpeg.progress.ProgressListener;
import org.apache.tools.ant.util.DateUtils;

import javax.swing.*;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class YouTube {

    public static YoutubeVideo getYouTubeVideo(String url) throws YoutubeException {
//        String url = "https://www.youtube.com/watch?v=KKEqQOsKERQ";
        Pattern pattern = Pattern.compile("v=(.{11})");
        Matcher match = pattern.matcher(url);
        YoutubeDownloader downloader = new YoutubeDownloader();
        if (match.find()) {
            String videoId = match.group(1);
            YoutubeVideo video = downloader.getVideo(videoId);
            return video;
        } else {
            return null;
        }
    }

    public static List<VideoFormat> getDirectURL(String url) throws YoutubeException {
//        String url = "https://www.youtube.com/watch?v=KKEqQOsKERQ";
        Pattern pattern = Pattern.compile("v=(\\w{11})");
        Pattern pattern2 = Pattern.compile("/^.*(?:(?:youtu\\.be\\/|v\\/|vi\\/|u\\/\\w\\/|embed\\/)|(?:(?:watch)?\\?v(?:i)?=|\\&v(?:i)?=))([^#\\&\\?]*).*/");
        Matcher match = pattern.matcher(url);
        YoutubeDownloader downloader = new YoutubeDownloader();
        if (match.find()) {
            String videoId = match.group(1);
            YoutubeVideo video = downloader.getVideo(videoId);
            VideoDetails details = video.details();
            List<VideoFormat> formats = video.videoFormats();
//            List<String> formats_list = null;
//            formats.forEach(it -> {
//                System.out.println(it.videoQuality());
////                formats_list.add(it.videoQuality().name());
//            });
            System.out.println(details.title());
            return formats;

        } else {
            System.out.println("Invalid URL");
            return null;
        }
    }
}
