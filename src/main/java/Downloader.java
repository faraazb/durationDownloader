//package components;

import com.github.kiulian.downloader.OnYoutubeDownloadListener;
import com.github.kiulian.downloader.YoutubeException;
import com.github.kiulian.downloader.model.Filter;
import com.github.kiulian.downloader.model.VideoDetails;
import com.github.kiulian.downloader.model.YoutubeVideo;
import com.github.kiulian.downloader.model.formats.Format;
import com.github.kiulian.downloader.model.formats.VideoFormat;
import net.bramp.ffmpeg.FFmpeg;
import net.bramp.ffmpeg.FFmpegExecutor;
import net.bramp.ffmpeg.FFmpegUtils;
import net.bramp.ffmpeg.FFprobe;
import net.bramp.ffmpeg.builder.FFmpegBuilder;
import net.bramp.ffmpeg.job.FFmpegJob;
import net.bramp.ffmpeg.probe.FFmpegProbeResult;
import net.bramp.ffmpeg.progress.Progress;
import net.bramp.ffmpeg.progress.ProgressListener;

import java.awt.*;
import java.awt.event.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.swing.*;
import javax.swing.event.*;


public class Downloader extends JPanel
        implements ListSelectionListener {
    private JList list;
    private DefaultListModel videoFormatsListModel;

    private static final String goString = "Go";
    private static final String downloadString = "Download";
    private JButton downloadButton;
    private JButton goButton;
    private JTextField videoLinkField;
    private JLabel titleChannel;
    private JTextField startTime;
    private JTextField endTime;
    private long length;
    private String stringLength;
    private JFrame invalidDataAlert;
    private String videoTitle;
    private JProgressBar progressBar;
    private JTextArea downloadOutput;
    private FfmpegTask ffmpegTask;
    private YtdlTask ytdlTask;
    private String processInfo;
    private String errorInfo;
    private YoutubeVideo video;

    public Downloader() {
        super(new BorderLayout());

        videoFormatsListModel = new DefaultListModel<VideoFormat>();

        //Create the list and put it in a scroll pane.
        list = new JList(videoFormatsListModel);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer(new VideoCellRenderer());
//        list.setSelectedIndex(0); /*This line can be used to select the first video format*/
        list.addListSelectionListener(this);
        list.setVisibleRowCount(5);
        JScrollPane formatsListScrollPane = new JScrollPane(list);

        /*The goButton is disbaled on creation, it is enabled when DocumnetListener implemented in GoListener
        * notifies that there has been input in the videoLinkField
        * The handling of the input such as checking for URL validity is done within GoListener's actionPerformed method
        * which is triggered when the button is pressed, and the button can only be pressed when there exists a
        * valid/invalid URL in the videoLinkField*/
        goButton = new JButton(goString);
        GoListener goListener = new GoListener(goButton);
        goButton.setActionCommand(goString);
        goButton.addActionListener(goListener);
        goButton.setEnabled(false);

        DownloadListener downloadListener = new DownloadListener();
        downloadButton = new JButton(downloadString);
        downloadButton.setActionCommand(downloadString);
        downloadButton.addActionListener(downloadListener);
        downloadButton.setEnabled(false);

        videoLinkField = new JTextField(10);
        videoLinkField.addActionListener(goListener);
        videoLinkField.getDocument().addDocumentListener(goListener);
        videoLinkField.setToolTipText("<html>Enter URL<br>You can also enter 11 character " +
                "video ID as \"v=XXXXXXXX\"</html>");
//        String name = listModel.getElementAt(
//                list.getSelectedIndex()).toString();

        /*Creates a panel that uses BoxLayout. Used to layout the output window, progress bar, duration pane and
        * button pane on top of each other, at the "page end" or "very bottom of the frame"*/
        JPanel buttonPane = new JPanel();
        buttonPane.setLayout(new BoxLayout(buttonPane,
                BoxLayout.LINE_AXIS));
        buttonPane.add(downloadButton);
        buttonPane.add(Box.createHorizontalStrut(5));
        buttonPane.add(new JSeparator(SwingConstants.VERTICAL));
        buttonPane.add(Box.createHorizontalStrut(5));
        buttonPane.add(videoLinkField);
        buttonPane.add(goButton);
        buttonPane.setBorder(BorderFactory.createEmptyBorder(5,5,5,5));


        //makes the header area where video title and channel name is displayed
        titleChannel = new JLabel("Paste YouTube link and press Go");
        titleChannel.setSize(600, 60);
//        titleChannel.setText();

        startTime = new JTextField(10);
        startTime.addActionListener(downloadListener);

        JLabel timeStart = new JLabel("Start Time:");
        JLabel timeEnd = new JLabel("End Time:");

        endTime = new JTextField(10);
        endTime.addActionListener(downloadListener);
        startTime.setEnabled(false);
        endTime.setEnabled(false);

        progressBar = new JProgressBar(0, 100);
        progressBar.setValue(0);
        progressBar.setStringPainted(true);
        downloadOutput = new JTextArea(2, 20);
        downloadOutput.setMargin(new Insets(5,5,5,5));
        downloadOutput.setLineWrap(true);
        downloadOutput.setEditable(false);
//        downloadOutput.setText("To wrap the lines of JTextArea we need to call the setLineWrap(boolean wrap)
//        method and pass a true boolean value as the parameter. ");

        JPanel durationPane = new JPanel();
        durationPane.setLayout(new BoxLayout(durationPane,
                BoxLayout.LINE_AXIS));
        durationPane.add(timeStart);
        durationPane.add(Box.createHorizontalStrut(5));
        durationPane.add(startTime);
        durationPane.add(Box.createHorizontalStrut(5));
        durationPane.add(timeEnd);
        durationPane.add(Box.createHorizontalStrut(5));
        durationPane.add(endTime);

        JPanel allPanes = new JPanel();
        allPanes.setLayout(new BoxLayout(allPanes, BoxLayout.PAGE_AXIS));
        allPanes.add(downloadOutput);
        allPanes.add(Box.createRigidArea(new Dimension(0,5)));
        allPanes.add(progressBar);
        allPanes.add(Box.createRigidArea(new Dimension(0,5)));
        allPanes.add(durationPane);
        allPanes.add(Box.createRigidArea(new Dimension(0,5)));
        allPanes.add(buttonPane);
        allPanes.setBorder(BorderFactory.createEmptyBorder(10,10,10,10));

        /*Finally adding all components to the JFrame in BorderLayout layout*/
        add(formatsListScrollPane, BorderLayout.CENTER);
        add(allPanes, BorderLayout.PAGE_END);
        add(titleChannel,BorderLayout.NORTH);

    }

    class FfmpegTask extends SwingWorker<Void, Void> {
        /*
         * Main task. Executed in background thread.
         * Ffmeg Task is used when custom start and end time are provided by the user
         * since ffmpeg can fetch only the required data and encode it to .mp4
         */
        String url; String start; String duration; String title;
        FfmpegTask(String url, String start, String duration, String title) {
            this.url = url;
            this.start = start;
            this.duration = duration;
            this.title = title;
        }

        @Override
        public Void doInBackground() throws IOException{
            String url = this.url;
            String title = this.title;
            String start = this.start;
            String duration = this.duration;
            int progress = 0;
            //Initialize progress property.
            setProgress(0);
            downloadButton.setEnabled(false);
            goButton.setEnabled(false);
            String home = System.getProperty("user.home");

            FFmpeg ffmpeg = new FFmpeg("F:\\tools\\ffmpeg.exe");
            FFprobe ffprobe = new FFprobe("F:\\tools\\ffprobe.exe");

            FFmpegExecutor executor = new FFmpegExecutor(ffmpeg, ffprobe);

            FFmpegProbeResult input = ffprobe.probe(url);
            FFmpegBuilder builder = new FFmpegBuilder()
                    .setInput(url)
                    .addOutput(home+"\\Downloads\\"+title+".mp4")
                    .addExtraArgs("-ss", start, "-t", duration)
                    .done();
            FFmpegJob job = executor.createJob(builder, new ProgressListener() {

                // Using the FFmpegProbeResult determine the duration of the input
                final double duration_ns = input.getFormat().duration * TimeUnit.SECONDS.toNanos(1);

                @Override
                public void progress(Progress progress) {
                    double percentage = progress.out_time_ns / duration_ns;

                    // Print out interesting information about the progress
                    setProgress((int) (percentage*100));
                    String oldInfo = processInfo;
                    processInfo = (String.format(
//                            "status:%s frame:%d time:%s ms fps:%.0f speed:%.2fx",
                            "Time:%s ms | Speed:%.2fx | FPS:%.0f | Frame:%d | Satus:%s",
//                            percentage * 100,
                            FFmpegUtils.toTimecode(progress.out_time_ns, TimeUnit.NANOSECONDS),
                            progress.speed,
                            progress.fps.doubleValue(),
                            progress.frame,
                            progress.status
                    ));
                    downloadOutput.setText(processInfo);
                    ffmpegTask.firePropertyChange("processInfo", oldInfo, processInfo);
                }
            });
            job.run();
            return null;
        }

        /*
         * Executed in event dispatching thread
         */
        @Override
        public void done() {
            Toolkit.getDefaultToolkit().beep();
            setProgress(100);
            downloadButton.setEnabled(true);
            goButton.setEnabled(true);
            setCursor(null); //turn off the wait cursor
//            downloadOutput.append("Done!\n");
        }
    }

    class YtdlTask extends SwingWorker<Void, Void> {
        /*
        * The YtldlTask is used when the start and end time are the default i.e. 00:00:00 and end time is equal to the
        * video duration. It uses the youtube-downloader-java module's async downloading method, instead of encoding the
        * video using ffmpeg as encoding it could result in more time and is unnecessary when the video is directly
        * received as a video file. This also means that videos downloaded with Ytdl have the same format as displayed
        * in formatListScrollPane, instead of .mp4 as the only supported output by FfmpegTask
        * */
        Format format;
        YtdlTask(Format format) {
            this.format = format;
        }

        @Override
        public Void doInBackground() throws YoutubeException, IOException{
            downloadButton.setEnabled(false);
            goButton.setEnabled(false);
            downloadOutput.setText("");
            String home = System.getProperty("user.home");
            File outputDir = new File(home+"\\Downloads\\");
//            File file = video.download(format, outputDir);
            Future<File> future = video.downloadAsync(format, outputDir, new OnYoutubeDownloadListener() {
                @Override
                public void onDownloading(int progress) {
//                    System.out.printf("Downloaded %d%%\n", progress);
                    setProgress(progress);
                }

                @Override
                public void onFinished(File file) {
                    Toolkit.getDefaultToolkit().beep();
                    setProgress(100);
                    setCursor(null);
                    downloadButton.setEnabled(true);
                    goButton.setEnabled(true);
                    downloadOutput.setText("Finished downloading: "+file);
//                    System.out.println("Finished file: " + file);
//                    firePropertyChange("completion", "", "");
                }

                @Override
                public void onError(Throwable throwable) {
                    errorInfo = "Error: " + throwable.getLocalizedMessage();
//                    System.out.println("Error: " + throwable.getLocalizedMessage());
                    firePropertyChange("YtdlError", "", errorInfo);
                }
            });
            return null;
        }

        /*
         * Executed in event dispatching thread
         */
        @Override
        public void done() {

        }
    }


    class DownloadListener implements ActionListener, PropertyChangeListener {
        public void actionPerformed(ActionEvent e) {
            /*
            * This method is called when the Download button is pressed, it does the necessary checking for
            * both startand end timestamps, decides which download Task to use (Ytdl or Ffmpeg) and executes the task
            * */
            downloadOutput.setText("");
            setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
            String message = "Enter timestamps in hh:mm:ss formats for both start and end time";
            String pattern = "^\\d\\d:\\d\\d:\\d\\d$";
            String start = startTime.getText();
            String end = endTime.getText();
            if (!start.matches(pattern) || !end.matches(pattern)) {
//                System.out.println("Sorry");
                invalidDataAlert = new JFrame();
                JOptionPane.showMessageDialog(invalidDataAlert, message, "Alert", JOptionPane.WARNING_MESSAGE);
                setCursor(null);
                return;
            }

            String[] start_time = start.split(":");
            String[] end_time = end.split(":");
            long start_seconds = (Integer.valueOf(start_time[0]) * 3600) + (Integer.valueOf(start_time[1]) * 60)
                    + (Integer.valueOf(start_time[2]));
            long end_seconds = (Integer.valueOf(end_time[0]) * 3600) + (Integer.valueOf(end_time[1]) * 60)
                    + (Integer.valueOf(end_time[2]));
//            System.out.println(start_seconds);
//            System.out.println(end_seconds);
            if (start_seconds >= end_seconds || end_seconds > length) {
                String invalidTimeOptions = "Either the start timestamp is ahead of end timestamp.\nOR\n"
                        + "End timestamp is ahead of the duration (" + stringLength + ") of the video.";
                invalidDataAlert = new JFrame();
                JOptionPane.showMessageDialog(invalidDataAlert, invalidTimeOptions,
                        "Alert", JOptionPane.WARNING_MESSAGE);
                setCursor(null);
                return;
            }

            int index = list.getSelectedIndex();

            Format selected_format = (Format) list.getModel().getElementAt(index);


            if (end.equals(stringLength) && start.equals("00:00:00")) {
                ytdlTask = new Downloader.YtdlTask(selected_format);
                ytdlTask.addPropertyChangeListener(this);
                ytdlTask.execute();
            } else {
                long duration_seconds = end_seconds - start_seconds;
                long hours = TimeUnit.SECONDS.toHours(duration_seconds) % 24;
                long minutes = TimeUnit.SECONDS.toMinutes(duration_seconds) % 60;
                long seconds = duration_seconds % 60;
                String duration = String.format("%02d:%02d:%02d",
                        hours, minutes, seconds);
//            System.out.println(duration);
//            System.out.println(selected_format.videoQuality().name());
                setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
                //Instances of javax.swing.SwingWorker are not reusuable, so
                //we create new instances as needed.
                ffmpegTask = new FfmpegTask(selected_format.url(), start, duration, videoTitle);
                ffmpegTask.addPropertyChangeListener(this);
                ffmpegTask.execute();
            }
        }

        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            if ("progress" == evt.getPropertyName()) {
                int progress = (Integer) evt.getNewValue();
                progressBar.setValue(progress);
            }
            if ("processInfo" == evt.getPropertyName()) {
                downloadOutput.setText(processInfo);
            }
            if ("YtdlError" == evt.getPropertyName()) {
                downloadOutput.setText(errorInfo);
            }
        }
    }

    //This listener is shared by the videoLinkfield and the Download button.
    class GoListener implements ActionListener, DocumentListener {
        private boolean alreadyEnabled = false;
        private JButton button;

        public GoListener(JButton button) {
            this.button = button;
        }

        //Required by ActionListener.
        public void actionPerformed(ActionEvent e) {
            String name = videoLinkField.getText();
            List<Format> formats = null;
//            YoutubeVideo video = null;
            VideoDetails details = null;
            try {
//                formats = YouTube.getDirectURL(name);
                video = YouTube.getYouTubeVideo(name);
            } catch (YoutubeException youtubeException) {
                youtubeException.printStackTrace();
            }
            if (video == null) {
                invalidDataAlert = new JFrame();
                String invalidUrl = "Enter a valid YouTube URL!\n" +
                        "E.g. https://www.youtube.com/watch?v=HgzGwKwLmgM\n" +
                        "If you got your link from a playlist, trim the link as " +
                        "https://www.yout..../watch?v=XXXXXXXXXXX";
                JOptionPane.showMessageDialog(invalidDataAlert, invalidUrl, "Alert", JOptionPane.WARNING_MESSAGE);
                return;
            }

            Filter<Format> filter = new Filter<Format>() {
                @Override
                public boolean test(Format format) {
                    if (format.type() == "audio") {
                        return false;
                    } else {
                        return true;
                    }
                }
            };
            formats = video.findFormats(filter);
            length = video.details().lengthSeconds();
            videoTitle = video.details().title();
            String channel = video.details().author();
            titleChannel.setText(videoTitle + " - " + channel);
            videoFormatsListModel.clear();
            for (Format format : formats) {
//                System.out.println(format.type());
                videoFormatsListModel.addElement(format);
            }


            int size = videoFormatsListModel.getSize();

            if(list.getSelectedIndex() == -1) {
                downloadButton.setEnabled(false);
            }
            if (size > 0) {
                startTime.setEnabled(true);
                endTime.setEnabled(true);
                startTime.setText("00:00:00");

                long hours = TimeUnit.SECONDS.toHours(length) % 24;
                long minutes = TimeUnit.SECONDS.toMinutes(length) % 60;
                long seconds = length % 60;
                stringLength = String.format("%02d:%02d:%02d",
                        hours, minutes, seconds);
                endTime.setText(stringLength);

            }
            if (size == 0) { //No video found
                downloadButton.setEnabled(false);
                invalidDataAlert = new JFrame();
                String noVideoMessage = "No video format was found! This is weird O_O";
                JOptionPane.showMessageDialog(invalidDataAlert, noVideoMessage,
                        "Alert", JOptionPane.WARNING_MESSAGE);
                return;
            }
        }


        //Required by DocumentListener.
        public void insertUpdate(DocumentEvent e) {
            enableButton();
        }

        //Required by DocumentListener.
        public void removeUpdate(DocumentEvent e) {
            handleEmptyTextField(e);
        }

        //Required by DocumentListener.
        public void changedUpdate(DocumentEvent e) {
            if (!handleEmptyTextField(e)) {
                enableButton();
            }
        }

        private void enableButton() {
            if (!alreadyEnabled) {
                button.setEnabled(true);
            }
        }

        private boolean handleEmptyTextField(DocumentEvent e) {
            if (e.getDocument().getLength() <= 0) {
                button.setEnabled(false);
                alreadyEnabled = false;
                return true;
            }
            return false;
        }
    }

    //This method is required by ListSelectionListener.
    public void valueChanged(ListSelectionEvent e) {
        if (e.getValueIsAdjusting() == false) {

            if (list.getSelectedIndex() == -1) {
                //No selection, disable Download button.
                downloadButton.setEnabled(false);

            } else {
                //Selection, enable the Download button.
                downloadButton.setEnabled(true);
            }
        }
    }

    /**
     * Create the GUI and show it.  For thread safety,
     * this method should be invoked from the
     * event-dispatching thread.
     */
    private static void createAndShowGUI() {
        //Create and set up the window.
        JFrame frame = new JFrame("team15 youtube-downloader");
        frame.setPreferredSize(new Dimension(500, 400));
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        //Create and set up the content pane.
        JComponent newContentPane = new Downloader();
        newContentPane.setOpaque(true); //content panes must be opaque
        frame.setContentPane(newContentPane);

        //Display the window.
        frame.pack();
        frame.setVisible(true);
    }

    public static void main(String[] args) {
        //Schedule a job for the event-dispatching thread:
        //creating and showing this application's GUI.
        javax.swing.SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                createAndShowGUI();
            }
        });
    }
}

class VideoCellRenderer extends JLabel implements ListCellRenderer<Format>{
    /*
    * This is used to display a Format object defined in the youtube-downloader-java module.
    * A Format object, represents a video's Format, one video generally has more than one format.
    * The Format object has methods to return that format's type (audio, video, audio/video), extension (mp4, webm)
    * quality (720p, large, 240p) and also has the URL of the video in this particular format that the object represents
    * This renderer defines how this object is displayed in a list*/
    private static final Color HIGHLIGHT_COLOR = new Color(0, 0, 128);

    public VideoCellRenderer() {
        setOpaque(true);
        setIconTextGap(12);
    }

    public Component getListCellRendererComponent(JList list, Format value,
                                                  int index, boolean isSelected, boolean cellHasFocus) {
//        VideoFormat entry = (VideoFormat) value;
//        setText(value.type() + " | " + value.qualityLabel() + " | " + value.extension().value());

//        The next line is the type of text displayed in the format selection pane.
        setText(value.type() + " | " + value.itag().videoQuality() + " | " + value.extension().value());
        if (isSelected) {
            setBackground(HIGHLIGHT_COLOR);
            setForeground(Color.white);
        } else {
            setBackground(Color.white);
            setForeground(Color.black);
        }
        return this;
    }
}