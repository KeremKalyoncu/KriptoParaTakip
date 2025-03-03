import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Timer;
import java.util.TimerTask;
import javax.sound.sampled.*;
import org.json.JSONObject;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class KriptoParaTakip {
    private JFrame frame;
    private DefaultListModel<String> listModel;
    private JList<String> cryptoList;
    private ConcurrentHashMap<String, Double> cryptoPrices;
    private ConcurrentHashMap<String, Double> previousPrices;
    private JTextField cryptoInput;
    private String selectedCrypto = "";
    private Timer timer;
    private boolean isRunning = true;
    private JLabel statusLabel;
    private ExecutorService soundExecutor;

    public KriptoParaTakip() {
        frame = new JFrame("Canlı Kripto Para Takip Uygulaması");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(600, 500);
        frame.setLayout(new BorderLayout());

        soundExecutor = Executors.newFixedThreadPool(3);

        JPanel titlePanel = new JPanel();
        JLabel titleLabel = new JLabel("Canlı Kripto Para Takip Uygulaması");
        titleLabel.setFont(new Font("Arial", Font.BOLD, 20));
        titlePanel.add(titleLabel);
        frame.add(titlePanel, BorderLayout.NORTH);

        listModel = new DefaultListModel<>();
        cryptoList = new JList<>(listModel);
        cryptoList.setFont(new Font("Monospaced", Font.BOLD, 16)); // Monospaced font kullan
        cryptoList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        cryptoList.setCellRenderer(new CryptoCellRenderer());

        cryptoList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && cryptoList.getSelectedValue() != null) {
                String newSelectedCrypto = cryptoList.getSelectedValue().split(" - ")[0];

                if (!newSelectedCrypto.equals(selectedCrypto)) {
                    selectedCrypto = newSelectedCrypto;
                    updateStatusLabel("Seçilen Kripto: " + selectedCrypto + " - Fiyat değişimlerinde ses bildirimi alacaksınız");
                    System.out.println("Seçilen Kripto: " + selectedCrypto);
                }
            }
        });

        JScrollPane scrollPane = new JScrollPane(cryptoList);
        frame.add(scrollPane, BorderLayout.CENTER);

        cryptoPrices = new ConcurrentHashMap<>();
        previousPrices = new ConcurrentHashMap<>();

        JPanel inputPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        cryptoInput = new JTextField(10);
        cryptoInput.setFont(new Font("Arial", Font.PLAIN, 14));

        cryptoInput.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    addCrypto();
                }
            }
        });

        JButton addButton = new JButton("Ekle");
        JButton removeButton = new JButton("Sil");

        addButton.setBackground(new Color(0, 120, 215));
        addButton.setForeground(Color.BLACK);
        addButton.setFocusPainted(false);

        removeButton.setBackground(new Color(232, 17, 35));
        removeButton.setForeground(Color.BLACK);
        removeButton.setFocusPainted(false);

        inputPanel.add(new JLabel("Kripto Kodu: "));
        inputPanel.add(cryptoInput);
        inputPanel.add(addButton);
        inputPanel.add(removeButton);

        statusLabel = new JLabel("Fiyatlar her saniye güncellenir. Ses bildirimleri için bir kripto seçin.");
        statusLabel.setHorizontalAlignment(SwingConstants.CENTER);
        statusLabel.setFont(new Font("Arial", Font.ITALIC, 12));

        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.add(inputPanel, BorderLayout.CENTER);
        bottomPanel.add(statusLabel, BorderLayout.SOUTH);

        frame.add(bottomPanel, BorderLayout.SOUTH);

        addButton.addActionListener(e -> addCrypto());

        removeButton.addActionListener(e -> {
            String inputText = cryptoInput.getText().toUpperCase().trim();
            if (!inputText.isEmpty() && cryptoPrices.containsKey(inputText)) {
                for (int i = 0; i < listModel.size(); i++) {
                    String item = listModel.getElementAt(i);
                    if (item.startsWith(inputText + " - ")) {
                        listModel.remove(i);
                        cryptoPrices.remove(inputText);
                        previousPrices.remove(inputText);
                        if (selectedCrypto.equals(inputText)) {
                            selectedCrypto = "";
                            updateStatusLabel("Fiyatlar her saniye güncellenir. Ses bildirimleri için bir kripto seçin.");
                        }
                        cryptoInput.setText("");
                        return;
                    }
                }
            }

            String selected = cryptoList.getSelectedValue();
            if (selected != null) {
                String cryptoName = selected.split(" - ")[0];
                listModel.removeElement(selected);
                cryptoPrices.remove(cryptoName);
                previousPrices.remove(cryptoName);
                if (selectedCrypto.equals(cryptoName)) {
                    selectedCrypto = "";
                    updateStatusLabel("Fiyatlar her saniye güncellenir. Ses bildirimleri için bir kripto seçin.");
                }
            }
        });

        frame.setLocationRelativeTo(null);
        frame.setVisible(true);

        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                cleanup();
            }
        });

        timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                if (isRunning) {
                    updatePrices();
                }
            }
        }, 0, 1000);
    }

    private void updateStatusLabel(String text) {
        SwingUtilities.invokeLater(() -> {
            statusLabel.setText(text);
        });
    }

    private void cleanup() {
        isRunning = false;
        if (timer != null) {
            timer.cancel();
            timer.purge();
        }

        if (soundExecutor != null) {
            soundExecutor.shutdown();
        }
    }

    private void addCrypto() {
        String crypto = cryptoInput.getText().toUpperCase().trim();
        if (!crypto.isEmpty()) {
            if (cryptoPrices.containsKey(crypto)) {
                JOptionPane.showMessageDialog(frame,
                        crypto + " zaten listenizde bulunuyor.",
                        "Uyarı",
                        JOptionPane.WARNING_MESSAGE);
                return;
            }

            updateStatusLabel("Kripto fiyatı alınıyor: " + crypto);
            SwingWorker<Double, Void> worker = new SwingWorker<Double, Void>() {
                @Override
                protected Double doInBackground() throws Exception {
                    return getCryptoPrice(crypto);
                }

                @Override
                protected void done() {
                    try {
                        double price = get();
                        if (price > 0) {
                            cryptoPrices.put(crypto, price);
                            previousPrices.put(crypto, price);
                            updateListModel();
                            cryptoInput.setText("");
                            updateStatusLabel("Fiyatlar her saniye güncellenir. Ses bildirimleri için bir kripto seçin.");
                        } else {
                            JOptionPane.showMessageDialog(frame,
                                    crypto + " geçerli bir kripto kodu değil veya Binance'da bulunamadı.",
                                    "Hata",
                                    JOptionPane.ERROR_MESSAGE);
                            updateStatusLabel("Fiyatlar her saniye güncellenir. Ses bildirimleri için bir kripto seçin.");
                        }
                    } catch (Exception e) {
                        JOptionPane.showMessageDialog(frame,
                                "Fiyat alınırken bir hata oluştu: " + e.getMessage(),
                                "Hata",
                                JOptionPane.ERROR_MESSAGE);
                        updateStatusLabel("Fiyatlar her saniye güncellenir. Ses bildirimleri için bir kripto seçin.");
                    }
                }
            };
            worker.execute();
        }
    }


    private void updatePrices() {
        if (cryptoPrices.isEmpty()) return;

        SwingWorker<Void, Void> worker = new SwingWorker<Void, Void>() {
            private boolean hasUpdates = false;

            @Override
            protected Void doInBackground() throws Exception {
                for (String crypto : cryptoPrices.keySet()) {
                    try {
                        double oldPrice = cryptoPrices.get(crypto);
                        double newPrice = getCryptoPrice(crypto);

                        if (newPrice > 0) {
                            if (Math.abs(newPrice - oldPrice) > 0.00000001) {
                                hasUpdates = true;
                                previousPrices.put(crypto, oldPrice);
                                cryptoPrices.put(crypto, newPrice);

                                if (crypto.equals(selectedCrypto)) {
                                    System.out.println("Seçili kripto fiyat değişimi: " + crypto +
                                            " Eski: " + oldPrice + " Yeni: " + newPrice);
                                    if (newPrice > oldPrice) {
                                        System.out.println("Fiyat arttı, ses çalınacak: up.wav");
                                        playSoundAsync("up.wav");
                                    } else if (newPrice < oldPrice) {
                                        System.out.println("Fiyat düştü, ses çalınacak: down.wav");
                                        playSoundAsync("down.wav");
                                    }
                                }
                            }
                        }
                    } catch (Exception e) {
                        System.out.println("Fiyat güncellenirken hata: " + e.getMessage());
                    }
                }
                return null;
            }

            @Override
            protected void done() {
                if (hasUpdates) {
                    updateListModel();
                }
            }
        };
        worker.execute();
    }

    private void updateListModel() {
        SwingUtilities.invokeLater(() -> {
            int selectedIndex = cryptoList.getSelectedIndex();

            listModel.clear();
            cryptoPrices.forEach((crypto, price) -> {
                double prevPrice = previousPrices.getOrDefault(crypto, price);
                String priceChange = "";
                if (price > prevPrice) {
                    priceChange = " ▲";
                } else if (price < prevPrice) {
                    priceChange = " ▼";
                }
                listModel.addElement(crypto + " - " + String.format("%.10f", price) + " USD" + priceChange);
            });

            if (selectedIndex >= 0 && selectedIndex < listModel.size()) {
                cryptoList.setSelectedIndex(selectedIndex);
            }
        });
    }

    private double getCryptoPrice(String symbol) {
        HttpURLConnection connection = null;
        BufferedReader reader = null;
        try {
            String url = "https://api.binance.com/api/v3/ticker/price?symbol=" + symbol + "USDT";
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);

            int responseCode = connection.getResponseCode();
            if (responseCode == 200) {
                reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                String response = reader.readLine();

                JSONObject json = new JSONObject(response);
                return json.getDouble("price");
            } else if (responseCode == 429) {
                Thread.sleep(1000);
                return getCryptoPrice(symbol);
            } else {
                System.out.println("HTTP Hata Kodu: " + responseCode);
                return 0.0;
            }
        } catch (Exception e) {
            System.out.println("Hata: " + e.getMessage());
            return 0.0;
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private void playSoundAsync(String fileName) {
        soundExecutor.submit(() -> {
            playSound(fileName);
        });
    }

    private void playSound(String fileName) {
        File soundFile = new File(fileName);
        System.out.println("Ses çalma denemesi: " + fileName);
        System.out.println("Çalışma dizini: " + System.getProperty("user.dir"));
        System.out.println("Ses dosyası tam yolu: " + soundFile.getAbsolutePath());
        System.out.println("Ses dosyası var mı: " + soundFile.exists());

        if (!soundFile.exists()) {
            System.out.println("HATA: Ses dosyası bulunamadı: " + fileName);

            updateStatusLabel("Ses dosyası bulunamadı: " + fileName + " (" + soundFile.getAbsolutePath() + ")");
            return;
        }

        Clip clip = null;
        AudioInputStream audioStream = null;
        try {
            audioStream = AudioSystem.getAudioInputStream(soundFile);
            clip = AudioSystem.getClip();
            clip.open(audioStream);
            clip.start();

            clip.addLineListener(event -> {
                if (event.getType() == LineEvent.Type.STOP) {
                    event.getLine().close();
                }
            });

            Thread.sleep(clip.getMicrosecondLength() / 1000);
        } catch (Exception e) {
            System.out.println("Ses çalarken hata oluştu: " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (audioStream != null) {
                try {
                    audioStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (clip != null && clip.isOpen() && !clip.isRunning()) {
                clip.close();
            }
        }
    }

    private class CryptoCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value,
                                                      int index, boolean isSelected, boolean cellHasFocus) {

            Component c = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

            if (value != null && value instanceof String) {
                String item = (String) value;
                String[] parts = item.split(" - ");

                if (parts.length >= 2) {
                    String crypto = parts[0];
                    double currentPrice = cryptoPrices.getOrDefault(crypto, 0.0);
                    double previousPrice = previousPrices.getOrDefault(crypto, currentPrice);

                    if (!isSelected) {
                        setBackground(index % 2 == 0 ? new Color(240, 240, 240) : Color.WHITE);
                    }

                    if (!isSelected) {
                        if (currentPrice > previousPrice) {
                            setForeground(new Color(0, 128, 0));
                        } else if (currentPrice < previousPrice) {
                            setForeground(new Color(220, 0, 0));
                        } else {
                            setForeground(Color.BLACK);
                        }
                    }
                }
            }

            return c;
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception e) {
                e.printStackTrace();
            }
            new KriptoParaTakip();
        });
    }
}

