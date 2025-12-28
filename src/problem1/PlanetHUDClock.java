package problem1;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.*;
import java.io.InputStream;
import javax.imageio.ImageIO;
import java.time.*;

public class PlanetHUDClock extends JFrame {
     
     // 時間基準
     enum TimeBase {
         SIM_X1,
         SIM_X10,
         SIM_X100,
         REAL_WORLD
     }

     private TimeBase timeBase = TimeBase.SIM_X1;
     private int selectedCityIndex = 0;
     private HUDCitySelectPanel citySelectPanel;
     private HUDButton timeButton;

     // ミッション時間（MET）
     private long missionStartMillis;
     private double timeScale = 1.0; // ×1, ×10, ×100 など
     
     // 世界都市
     record City(String name,ZoneId zone) {}

     private static final City[] WORLD_CITIES = {
         new City("UTC", ZoneId.of("UTC")),

         new City("Tokyo", ZoneId.of("Asia/Tokyo")),
         new City("Beijing", ZoneId.of("Asia/Shanghai")),
         new City("Delhi", ZoneId.of("Asia/Kolkata")),
         new City("Riyadh", ZoneId.of("Asia/Riyadh")),

         new City("Moscow", ZoneId.of("Europe/Moscow")),
         new City("Berlin", ZoneId.of("Europe/Berlin")),
         new City("Paris", ZoneId.of("Europe/Paris")),
         new City("London", ZoneId.of("Europe/London")),

         new City("Sao Paulo", ZoneId.of("America/Sao_Paulo")),
         new City("New York", ZoneId.of("America/New_York")),
         new City("Los Angeles", ZoneId.of("America/Los_Angeles")),
         new City("Honolulu", ZoneId.of("Pacific/Honolulu")),

         new City("Sydney", ZoneId.of("Australia/Sydney"))
     };


     //惑星の自転周期　（地球時間1秒あたり惑星時間が何秒進むか）
     private static final double MERCURY_ROT = 58.646 * 86400;    // 水星
     private static final double VENUS_ROT   = -243.018 * 86400;  // 金星（逆向き自転）
     private static final double EARTH_ROT   = 1.0 * 86400;       // 地球
     private static final double MOON_ROT    = 27.32 * 86400;     // 月
     private static final double MARS_ROT    = 1.025957 * 86400;  // 火星
     private static final double JUPITER_ROT = 0.41 * 86400;      // 木星
     private static final double SATURN_ROT  = 0.44 * 86400;      // 土星
     private static final double URANUS_ROT  = -0.72 * 86400;     // 天王星 （横倒し+逆向き自転）
     private static final double NEPTUNE_ROT = 0.67 * 86400;      // 海王星

     private HUDClockLabel mercuryClock;
     private HUDClockLabel venusClock;
     private HUDClockLabel earthClock;
     private HUDClockLabel moonClock;
     private HUDClockLabel marsClock;
     private HUDClockLabel jupiterClock;
     private HUDClockLabel saturnClock;
     private HUDClockLabel uranusClock;
     private HUDClockLabel neptuneClock;


     public PlanetHUDClock() {
         setTitle("Planet HUD Clocks");

         // スケーリング対応パネル
         ScaledPanel bg = new ScaledPanel();
         bg.setLayout(null);
         setContentPane(bg);

         // 背景クリックで選択パネルを閉じる
         bg.addMouseListener(new MouseAdapter() {
              @Override
              public void mousePressed(MouseEvent e) {

                  // クリック座標
                  Point p = e.getPoint();

                  // MODE選択パネルが開いていて、外をクリックしたら閉じる
                  if (selectPanel.isVisible() &&
                      !selectPanel.getBounds().contains(p)) {
                      selectPanel.close();
                  }

                  // 都市選択パネルが開いていて、外をクリックしたら閉じる
                  if (citySelectPanel.isVisible() &&
                      !citySelectPanel.getBounds().contains(p)) {
                      citySelectPanel.close();
                  }
               }
           });
         
         // HUD風時計を生成
         mercuryClock = new HUDClockLabel("Mercury", 160, 400, 200, 80);
         venusClock   = new HUDClockLabel("Venus",   300, 335, 200, 80);
         earthClock   = new HUDClockLabel("Earth",   420, 700, 200, 80);
         moonClock    = new HUDClockLabel("Moon",    490, 310, 200, 80);
         marsClock    = new HUDClockLabel("Mars",    580, 380, 200, 80);
         jupiterClock = new HUDClockLabel("Jupiter", 900, 330, 200, 80);
         saturnClock  = new HUDClockLabel("Saturn", 1230, 360, 200, 80);
         uranusClock  = new HUDClockLabel("Uranus", 1500, 360, 200, 80);
         neptuneClock = new HUDClockLabel("Neptune",1700, 360, 200, 80);
         

         // HUD追加
         bg.add(mercuryClock);
         bg.add(venusClock);
         bg.add(earthClock);
         bg.add(moonClock);
         bg.add(marsClock);
         bg.add(jupiterClock);
         bg.add(saturnClock);
         bg.add(uranusClock);
         bg.add(neptuneClock);

         // 背景画像パネル
         BackgroundPanel back = new BackgroundPanel("/background2.jpg");
         bg.add(back);
         bg.setBackgroundPanel(back);


         // 時間初期化
         missionStartMillis = System.currentTimeMillis();
         
         // 時間更新タイマー
         Timer timer = new Timer(100, e -> updateClocks());
         timer.start();

         selectPanel = new HUDSelectPanel(20, 140, 300, 320);
         bg.add(selectPanel);

         // 都市選択パネル
         citySelectPanel = new HUDCitySelectPanel(300, 140, 260, 420);
         bg.add(citySelectPanel);

         // HUDボタン（時間切り替え）
         timeButton = new HUDButton(
            "CITY / MODE SWITCH",
            20, 20, 260, 48,
            () -> {
                citySelectPanel.close();
                selectPanel.open();
            }
        );

        bg.add(timeButton);

        HUDStatusLabel status = new HUDStatusLabel(
            20, 80, 260, 60,
            this::getStatusLines
        );
        bg.add(status);
        timeButton.setText(getTimeButtonLabel());
     }

private HUDSelectPanel selectPanel;

private void updateTimeScale() {
    switch (timeBase) {
        case SIM_X1   -> timeScale = 1.0;
        case SIM_X10  -> timeScale = 10.0;
        case SIM_X100 -> timeScale = 100.0;
        default       -> timeScale = 1.0;
    }
}

// MET(Mission Elapsed Time) を秒で取得
private double getMETSeconds() {
    long now = System.currentTimeMillis();
    return (now - missionStartMillis) / 1000.0 * timeScale;
}

private double getEarthSeconds() {

     if (timeBase != TimeBase.REAL_WORLD) {
         // ミッション経過時間(MET)
         return getMETSeconds();

     } else {
         // 実世界時間（都市）
         City c = WORLD_CITIES[selectedCityIndex];
         ZonedDateTime zt = ZonedDateTime.now(c.zone());

         return zt.toLocalTime().toSecondOfDay()
              + zt.getNano() / 1_000_000_000.0;
     }
}

private void updateClocks() {

   double earthSeconds = getEarthSeconds();


   // 各惑星の時間を計算してセット
   mercuryClock.setTime(earthSeconds * (86400 / MERCURY_ROT) );
   venusClock.setTime(earthSeconds * (86400 / VENUS_ROT) );
   earthClock.setTime(earthSeconds );
   moonClock.setTime(earthSeconds * (86400 / MOON_ROT) );
   marsClock.setTime(earthSeconds * (86400 / MARS_ROT) );
   jupiterClock.setTime(earthSeconds * (86400 / JUPITER_ROT) );
   saturnClock.setTime(earthSeconds * (86400 / SATURN_ROT) );
   uranusClock.setTime(earthSeconds * (86400 / URANUS_ROT) );
   neptuneClock.setTime(earthSeconds * (86400 / NEPTUNE_ROT) );

}

private String[] getStatusLines() {

    if (timeBase == TimeBase.REAL_WORLD) {
        City c = WORLD_CITIES[selectedCityIndex];
        return new String[] {
            "MODE : REAL WORLD",
            "CITY : "+ c.name()
        };
     }

     return new String[] {
         "MODE : SIM x" + (int) timeScale,
         "BASE : MET"
     };
}

private String getTimeButtonLabel() {
    if (timeBase == TimeBase.REAL_WORLD) {
        City c = WORLD_CITIES[selectedCityIndex];
        return  "CITY : " + c.name();
    } else {
        return "MODE : SIM x" + (int) timeScale;
    }
}

// ▼ 背景画像パネル
class ScaledPanel extends JPanel {
    private static final int BASE_W = 1920;
    private static final int BASE_H = 1080;

    private BackgroundPanel background;

    void setBackgroundPanel(BackgroundPanel p) {
       background = p;
    }

    @Override
    public void doLayout() {
        double scaleX = getWidth() / (double) BASE_W;
        double scaleY = getHeight() / (double) BASE_H;
        double scale = Math.min(scaleX, scaleY);

        // 先にHUD配置
        for (Component c : getComponents()) {
            if (c == background) continue;

            if (c instanceof ScalableComponent sc) {
                int x = (int) (sc.baseX() * scale);
                int y = (int) (sc.baseY() * scale);
                int w = (int) (sc.baseW() * scale);
                int h = (int) (sc.baseH() * scale);

             // HUD最小サイズ保証
             if (c instanceof HUDButton || c instanceof HUDStatusLabel || c instanceof HUDSelectPanel || c instanceof HUDCitySelectPanel) {
                 w = Math.max(w, sc.baseW() * 2 / 3);
                 h = Math.max(h, sc.baseH() * 2 / 3);
             }
                c.setBounds(x, y, w, h);
             }
          }

          // 最後に背景を全面配置
          if (background != null)
            background.setBounds(0, 0, getWidth(), getHeight());

          // 背景を最背面へ
          if (background != null) {
              setComponentZOrder(background, getComponentCount() - 1);
    }

  }

}

// 位置・サイズを保持するインターフェース
interface ScalableComponent {
    int baseX();
    int baseY();
    int baseW();
    int baseH();
}

// 背景画像パネル
class BackgroundPanel extends JPanel {
    private BufferedImage img;

    BackgroundPanel(String res) {
        try (InputStream is = getClass().getResourceAsStream(res)) {
            if (is != null) img = ImageIO.read(is);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (img != null)
            g.drawImage(img, 0, 0, getWidth(), getHeight(), this);

}

}

// HUD 時計ラベル（座標保持つき)
class HUDClockLabel extends JLabel implements ScalableComponent {

    private double time;
    private final String name;

    private final int bx, by, bw, bh;

    HUDClockLabel(String name, int x, int y, int w, int h) {
        this.name = name;
        this.bx = x; this.by = y; this.bw = w; this.bh = h;
        setForeground(new Color(180, 255, 255));
        setOpaque(false);
   }

   @Override public int baseX() { return bx; }
   @Override public int baseY() { return by; }
   @Override public int baseW() { return bw; }
   @Override public int baseH() { return bh; }

   void setTime(double sec) {
       time = sec;
       repaint();
   }

   @Override
   protected void paintComponent(Graphics g) {
       super.paintComponent(g);
       Graphics2D g2 = (Graphics2D) g.create();
       g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);


        // 背景の半透明HUDパネル
        g2.setColor(new Color(0, 60, 80, 120));
        g2.fillRoundRect(0, 0, getWidth(), getHeight(), 20, 20);

        // 光る外枠
        g2.setFont(new Font("Monospaced", Font.BOLD,22));
        g2.setColor(new Color(120, 240, 255, 180));
        g2.drawString(name, 10, 22);
        
        // 時計文字列
        String timeText = format(time);

        // メイン時計（光エフェクト）
        Font baseFont = new Font("Monospaced", Font.BOLD, 28);
        g2.setFont(baseFont);
        
        FontMetrics fm = g2.getFontMetrics();
        int tw = fm.stringWidth(timeText);
        int availableWidth = getWidth() - 20;
        
        // 文字が枠より大きい時だけ縮小
        if (tw > availableWidth) {
            float ratio = (float) availableWidth / tw;
            int newSize = (int) (baseFont.getSize() * ratio);
            newSize = Math.max(newSize, 8); // 最小サイズ
            baseFont = baseFont.deriveFont((float) newSize);
            g2.setFont(baseFont);
            fm = g2.getFontMetrics();
        }

        // 垂直中央揃え
        int y = (getHeight() + fm.getAscent()) / 2;
     
        // 時計本体描画
        g2.setColor(new Color(180, 255, 255));
        g2.drawString(timeText, 10, y);

        g2.dispose();
}

private String format(double sec) {
        sec = (sec % 86400 + 86400) % 86400;
        int h = (int)(sec / 3600);
        int m = (int)((sec % 3600) / 60);
        double s = sec % 60;
        return String.format("%02d:%02d:%05.2f", h, m, s);
}

}

// HUD ステータス表示　（モード・都市）
class HUDStatusLabel extends JComponent implements ScalableComponent {

    private final int bx, by, bw, bh;
    private final java.util.function.Supplier<String[]> linesSupplier;

    HUDStatusLabel(int x, int y, int w, int h,
                   java.util.function.Supplier<String[]> linesSupplier) {
        this.bx = x; this.by = y; this.bw = w; this.bh = h;
        this.linesSupplier = linesSupplier;
        setOpaque(false);
    }

    @Override public int baseX() { return bx; }
    @Override public int baseY() { return by; }
    @Override public int baseW() { return bw; }
    @Override public int baseH() { return bh; }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                            RenderingHints.VALUE_ANTIALIAS_ON);

        g2.setColor(new Color(0, 60, 80, 120));
        g2.fillRoundRect(0, 0, getWidth(), getHeight(), 18, 18);

        g2.setFont(new Font("Monospaced", Font.BOLD, 16));
        g2.setColor(new Color(180, 255, 255));

        int y = 22;
        for (String line : linesSupplier.get()) {
            g2.drawString(line, 10, y);
            y += 18;
        }

        g2.dispose();
     }
  }

// 選択肢の展開
class HUDSelectPanel extends JComponent implements ScalableComponent {

    private final int bx, by, bw, bh;

    private final String[] items = {
        "SIM x1",
        "SIM x10",
        "SIM x100",
        "REAL WORLD"
   };

    private final int ITEM_H = 36;
    private int hoverIndex = -1;


    HUDSelectPanel(int x, int y, int w, int h) {
        this.bx = x; this.by = y; this.bw = w; this.bh = h;
        setLayout(null);
        setVisible(false);

        // マウス処理
        addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                int idx = e.getY() / ITEM_H;
                hoverIndex = (idx >= 0 && idx < items.length) ? idx : -1;
                repaint();
            }
         });


     // クリック処理
     addMouseListener(new MouseAdapter() {
         @Override
         public void mousePressed(MouseEvent e) {
             int index = e.getY() / ITEM_H;
             if (index >= 0 && index < items.length) {
                 onSelect(index);
             }
         }
      });
    }

    @Override public int baseX() { return bx; }
    @Override public int baseY() { return by; }
    @Override public int baseW() { return bw; }
    @Override public int baseH() { return bh; }

    void open()  { setVisible(true); repaint(); }
    void close() { setVisible(false); repaint(); }


    // 選択されたときの処理
    private void onSelect(int index) {
        switch (index) {
            case 0 -> {
                timeBase = TimeBase.SIM_X1;
                updateTimeScale();
            }
            case 1 -> {
                timeBase = TimeBase.SIM_X10;
                updateTimeScale();
            }
            case 2 -> {
                timeBase = TimeBase.SIM_X100;
                updateTimeScale();
            }
            case 3 -> {
                timeBase = TimeBase.REAL_WORLD;
                timeScale = 1.0;
            }
         }
         
         if (timeBase != TimeBase.REAL_WORLD) {
             missionStartMillis = System.currentTimeMillis();
             citySelectPanel.close();
         } else {
             citySelectPanel.open();
         }

         updateClocks();
         timeButton.setText(getTimeButtonLabel());
         getParent().repaint();
         close(); // 選択後に収納
      }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                            RenderingHints.VALUE_ANTIALIAS_ON);

        // 背景パネル
        g2.setColor(new Color(0, 40, 60, 200));
        g2.fillRoundRect(0, 0, getWidth(), getHeight(), 20, 20);

        g2.setFont(new Font("Monospaced", Font.BOLD, 16));

        for(int i = 0; i < items.length; i++) {
           int y = i * ITEM_H;

           // hover 表示
           if (i == hoverIndex) {
               g2.setColor(new Color(0, 120, 160, 180));
               g2.fillRect(0, y, getWidth(), ITEM_H);
           }  

           g2.setColor(new Color(180, 255, 255));
           g2.drawString(items[i], 12, y + 24);
        }

        g2.dispose();
    }
}

// 都市選択パネル
class HUDCitySelectPanel extends JComponent implements ScalableComponent {

    private final int bx, by, bw, bh;
    private int hoverIndex = -1;
    private final int ITEM_H = 32;
    private int scrollOffset = 0;

    HUDCitySelectPanel(int x, int y, int w, int h) {
       this.bx = x; this.by = y; this.bw =w; this.bh = h;
       setVisible(false);

       addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                int idx = scrollOffset + e.getY() / ITEM_H;
                hoverIndex = (idx >= 0 && idx < WORLD_CITIES.length) ? idx : -1;
                repaint();
            }
       });

       addMouseListener(new MouseAdapter() {
           @Override
           public void mousePressed(MouseEvent e) {
               int index = scrollOffset +e.getY() / ITEM_H;
               if (index >= 0 && index < WORLD_CITIES.length) {
                   selectedCityIndex = index;
                   updateClocks();
                   timeButton.setText(getTimeButtonLabel());
                   close();
               }
           }

           @Override
           public void mouseExited(MouseEvent e) {
               hoverIndex = -1;
               repaint();
           }
       });

    // スクロール対応
    addMouseWheelListener(e -> {
        int visibleCount = Math.max(1, getHeight() / ITEM_H);
        int maxOffset = Math.max(0, WORLD_CITIES.length - visibleCount);

        scrollOffset += e.getWheelRotation();
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxOffset)); 


        repaint();
     });
  }
 
    @Override public int baseX() { return bx; }
    @Override public int baseY() { return by; }
    @Override public int baseW() { return bw; }
    @Override public int baseH() { return bh; }

    void open()  { setVisible(true); repaint(); }
    void close() { setVisible(false); repaint(); }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                            RenderingHints.VALUE_ANTIALIAS_ON);

        g2.setColor(new Color(0, 40, 60, 220));
        g2.fillRoundRect(0, 0, getWidth(), getHeight(), 20, 20);

        g2.setFont(new Font("Monospaced", Font.BOLD, 14));

        int visibleCount = Math.max(1, getHeight() / ITEM_H);
        int start = scrollOffset;
        int end = Math.min(start + visibleCount, WORLD_CITIES.length);

        for (int i = start; i < end; i++) {
            int y = (i - start) * ITEM_H;

            if(i == hoverIndex) {
               g2.setColor(new Color(0, 120, 160, 180));
               g2.fillRect(0, y, getWidth(), ITEM_H);
            }

            g2.setColor(new Color(180, 255, 255));
            g2.drawString(WORLD_CITIES[i].name(), 10, y + 22);
        }

        g2.dispose();
     }
}

// HUD ボタン　（JComponent版)
class HUDButton extends JComponent implements ScalableComponent {

    private final int bx, by, bw, bh;
    private boolean hover = false;
    private String text;
    private final Runnable action;

    HUDButton(String text, int x, int y, int w, int h, Runnable action) {
        this.text = text;
        this.bx = x; this.by = y; this.bw = w; this.bh = h;
        this.action = action;

        addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) {
                hover = true;
                repaint();
            }
            @Override public void mouseExited(MouseEvent e) {
                hover = false;
                repaint();
            }
            @Override public void mousePressed(MouseEvent e) {
                if (action != null) action.run();
            }
         });
      }

      @Override public int baseX() { return bx; }
      @Override public int baseY() { return by; }
      @Override public int baseW() { return bw; }
      @Override public int baseH() { return bh; }

      void setText(String newText) {
          this.text = newText;
          repaint();
      }

      @Override
      protected void paintComponent(Graphics g) {
          Graphics2D g2 = (Graphics2D) g.create();
          g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                              RenderingHints.VALUE_ANTIALIAS_ON);

          // 背景
          g2.setColor(hover
              ? new Color(0, 120, 160, 180)
              : new Color(0, 60, 80, 140));
          g2.fillRoundRect(0, 0, getWidth(), getHeight(), 18, 18);

          // 外枠
          g2.setColor(new Color(120, 240, 255, hover ? 220 : 160));
          g2.drawRoundRect(0, 0, getWidth() -1, getHeight() - 1, 18, 18);

          // テキスト中央
          Font baseFont = new Font("Monospaced", Font.BOLD, 18);
          g2.setFont(baseFont);
 
          FontMetrics fm = g2.getFontMetrics();
          int textWidth = fm.stringWidth(text);
          int maxWidth = getWidth() - 16;

          // はみ出す場合はフォント縮小
          if (textWidth > maxWidth) {
              float ratio = (float) maxWidth / textWidth;
              int newSize = Math.max(10, (int)(baseFont.getSize() * ratio));
              baseFont = baseFont.deriveFont((float)newSize);
              g2.setFont(baseFont);
              fm = g2.getFontMetrics();
          }

          int tx = (getWidth() - fm.stringWidth(text)) / 2;
          int ty = (getHeight() + fm.getAscent()) / 2 - 2;
          g2.drawString(text, tx, ty);

          g2.dispose();
        }
     }


// ▼ メインメソッド（ウィンドウ起動）
public static void main(String[] args) {
    PlanetHUDClock frame = new PlanetHUDClock();
    frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    
    frame.setSize(1280, 720); 
    frame.setLocationRelativeTo(null);
    frame.setVisible(true);
    
    frame.revalidate();
    frame.repaint();
  }

}