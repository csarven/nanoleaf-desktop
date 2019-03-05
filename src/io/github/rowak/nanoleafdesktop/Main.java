package io.github.rowak.nanoleafdesktop;

import java.awt.EventQueue;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import javax.swing.DefaultListModel;
import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JPanel;

import net.miginfocom.swing.MigLayout;
import java.awt.Color;
import java.awt.Dimension;

import javax.swing.border.LineBorder;

import io.github.rowak.Aurora;
import io.github.rowak.Effect;
import io.github.rowak.StatusCodeException;
import io.github.rowak.nanoleafdesktop.tools.PropertyManager;
import io.github.rowak.nanoleafdesktop.tools.UpdateManager;
import io.github.rowak.nanoleafdesktop.tools.Version;
import io.github.rowak.nanoleafdesktop.ui.button.*;
import io.github.rowak.nanoleafdesktop.ui.dialog.AuroraFinder;
import io.github.rowak.nanoleafdesktop.ui.dialog.LoadingSpinner;
import io.github.rowak.nanoleafdesktop.ui.dialog.OptionDialog;
import io.github.rowak.nanoleafdesktop.ui.dialog.SingleEntryDialog;
import io.github.rowak.nanoleafdesktop.ui.dialog.TextDialog;
import io.github.rowak.nanoleafdesktop.ui.dialog.colorpicker.BrightnessSlider;
import io.github.rowak.nanoleafdesktop.ui.dialog.colorpicker.ColorPicker;
import io.github.rowak.nanoleafdesktop.ui.dialog.colorpicker.ColorWheel;
import io.github.rowak.nanoleafdesktop.ui.listener.*;
import io.github.rowak.nanoleafdesktop.ui.panel.DiscoveryPanel;
import io.github.rowak.nanoleafdesktop.ui.panel.ambilight.AmbilightPanel;
import io.github.rowak.nanoleafdesktop.ui.panel.panelcanvas.PanelCanvas;
import io.github.rowak.nanoleafdesktop.ui.panel.spotify.SpotifyPanel;
import io.github.rowak.nanoleafdesktop.ui.scrollbar.ModernScrollBarUI;
import io.github.rowak.nanoleafdesktop.ui.slider.ModernSliderUI;

import java.awt.Font;
import java.awt.GridBagLayout;

import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JScrollPane;
import javax.swing.border.TitledBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.plaf.InsetsUIResource;

import org.json.JSONObject;

import com.github.kevinsawicki.http.HttpRequest.HttpRequestException;

import java.awt.Component;
import javax.swing.JTabbedPane;
import javax.swing.UIManager;
import javax.swing.JToggleButton;
import javax.swing.JSlider;
import javax.swing.JButton;

public class Main extends JFrame
{
	public static final Version VERSION = new Version("v0.5.0", true);
	public static final String VERSION_HOST =
			"https://api.github.com/repos/rowak/nanoleaf-desktop/releases";
	public static final String GIT_REPO = "https://github.com/rowak/nanoleaf-desktop";
	public static final String PROPERTIES_FILEPATH =
			System.getProperty("user.home") + "/properties.txt";
	
	private final int DEFAULT_WINDOW_WIDTH = 1050;
	private final int DEFAULT_WINDOW_HEIGHT = 800;
	
	private boolean adjusting;
	private Aurora device;
	private DefaultListModel<String> regularEffects, rhythmEffects;
	
	private JPanel contentPane;
	private PanelCanvas canvas;
	private DiscoveryPanel discoveryPanel;
	private AmbilightPanel ambilightPanel;
	private JLabel lblTitle;
	private JLabel lblActiveScene;
	private JSlider brightnessSlider, ctSlider;
	private JToggleButton btnOnOff;
	private JScrollPane scrlPaneRegEffects;
	private JScrollPane scrlPaneRhythEffects;
	private JList<String> regularEffectsList;
	private JList<String> rhythmEffectsList;
	
	public Main()
	{
		regularEffects = new DefaultListModel<String>();
		rhythmEffects = new DefaultListModel<String>();
		
		PropertyManager manager = new PropertyManager(PROPERTIES_FILEPATH);
		String lastSession = manager.getProperty("lastSession");
		
		// Use the device from the last session
		if (lastSession != null)
		{
			setupOldAurora(lastSession);
		}
		
		initUI();
		
		// Search for a a new device
		if (lastSession == null)
		{
			setupNewAurora();
		}
		
		checkForUpdate();
	}
	
	private void checkForUpdate()
	{
		new Thread(() ->
		{
			try
			{
				UpdateManager manager = new UpdateManager(VERSION_HOST, GIT_REPO);
				if (manager.updateAvailable(VERSION))
				{
					manager.showUpdateMessage(this);
				}
			}
			catch (HttpRequestException hre)
			{
				/*
				 * If the update server cannot be reached, ignore it (don't notify the user).
				 * The user will be notified about an update the next time they
				 * connect to the network and open the application.
				 */
			}
		}).start();
	}
	
	private int getUserWindowWidth()
	{
		PropertyManager manager = new PropertyManager(PROPERTIES_FILEPATH);
		String width = manager.getProperty("windowWidth");
		if (width != null)
		{
			return Integer.parseInt(width);
		}
		return DEFAULT_WINDOW_WIDTH;
	}
	
	private int getUserWindowHeight()
	{
		PropertyManager manager = new PropertyManager(PROPERTIES_FILEPATH);
		String height = manager.getProperty("windowHeight");
		if (height != null)
		{
			return Integer.parseInt(height);
		}
		return DEFAULT_WINDOW_HEIGHT;
	}
	
	public PanelCanvas getCanvas()
	{
		return this.canvas;
	}
	
	public void setDevice(Aurora device)
	{
		this.device = device;
		PropertyManager manager = new PropertyManager(PROPERTIES_FILEPATH);
		manager.setProperty("lastSession",
				device.getHostName() + " " +
				device.getPort() + " v1 " +
				device.getAccessToken());
		loadAuroraData();
		loadDeviceName();
		canvas.setAurora(device);
		canvas.initCanvas();
		discoveryPanel.setAurora(device);
		ambilightPanel.setAurora(device);
	}
	
	public void loadEffects() throws StatusCodeException
	{
		EventQueue.invokeLater(new Runnable()
		{
			public void run()
			{
				new Thread(() ->
				{
					try
					{
						for (Effect effect : device.effects().getAllEffects())
						{
							if (effect.getAnimType() == Effect.Type.PLUGIN &&
									effect.getPluginType().equals("rhythm"))
							{
								rhythmEffects.addElement(effect.getName());
							}
							else
							{
								regularEffects.addElement(effect.getName());
							}
						}
					}
					catch (StatusCodeException sce)
					{
						sce.printStackTrace();
					}
					
					if (regularEffects.size() > 0)
					{
						scrlPaneRegEffects.setViewportView(regularEffectsList);
					}
					if (rhythmEffects.size() > 0)
					{
						scrlPaneRhythEffects.setViewportView(rhythmEffectsList);
					}
				}).start();
			}
		});
	}
	
	public void loadStateComponents() throws StatusCodeException
	{
		if (device.state().getOn())
		{
			btnOnOff.setText("Turn Off");
		}
		else
		{
			btnOnOff.setText("Turn On");
		}
		
		brightnessSlider.setValue(device.state().getBrightness());
		ctSlider.setValue(device.state().getColorTemperature());
		
		loadActiveScene();
	}
	
	public void loadActiveScene() throws StatusCodeException
	{
		String currentEffect = device.effects().getCurrentEffectName();
		lblActiveScene.setText(currentEffect);
	}
	
	private void loadAuroraData()
	{
		try
		{
			loadEffects();
			loadStateComponents();
		}
		catch (StatusCodeException sce)
		{
			sce.printStackTrace();
		}
	}
	
	private void setupOldAurora(String lastSession)
	{
		String[] data = lastSession.split(" ");
		try
		{
			device = new Aurora(data[0],
					Integer.parseInt(data[1]),
					data[2], data[3]);
			EventQueue.invokeLater(() ->
			{
				loadDeviceName();
			});
		}
		catch (StatusCodeException | HttpRequestException schre)
		{
			new TextDialog(Main.this, "Failed to connect to the device. " +
					"Please try again.").setVisible(true);
		}
		catch (Exception e)
		{
			e.printStackTrace();
			OptionDialog errorDialog = new OptionDialog(this,
					"The data file has been modified or has become corrupt. " +
					"Would you like to fix this now?", "Yes", "No",
					new ActionListener()
					{
						@Override
						public void actionPerformed(ActionEvent e)
						{
							new PropertyManager(PROPERTIES_FILEPATH)
								.removeProperty("lastSession");
							OptionDialog dialog = (OptionDialog)((JButton)e.getSource())
										.getTopLevelAncestor();
							dialog.dispose();
							new TextDialog(Main.this,
									"Relaunch the application to setup a new device.")
									.setVisible(true);
						}
					},
					new ActionListener()
					{
						@Override
						public void actionPerformed(ActionEvent e)
						{
							OptionDialog dialog = (OptionDialog)((JButton)e.getSource())
									.getTopLevelAncestor();
							dialog.dispose();
						}
					});
			errorDialog.setVisible(true);
			EventQueue.invokeLater(() ->
			{
				errorDialog.requestFocus();
			});
		}
	}
	
	private void setupNewAurora()
	{
		new Thread(() ->
		{
			AuroraFinder finder = new AuroraFinder(Main.this);
			finder.setVisible(true);
			new Timer().scheduleAtFixedRate(new TimerTask()
			{
				public void run()
				{
					if (finder.getAccessToken() != null)
					{
						try
						{
							device = new Aurora(finder.getHostName(),
									finder.getPort(), "v1", finder.getAccessToken());
							this.cancel();
							
							PropertyManager manager = new PropertyManager(PROPERTIES_FILEPATH);
							manager.setProperty("lastSession",
									device.getHostName() + " " +
									device.getPort() + " v1 " +
									device.getAccessToken());
							lblTitle.setText("Connected to " + device.getName());
							Map<String, Object> devices = getDevices();
							if (devices.containsKey(device.getHostName()))
							{
								loadDeviceName();
							}
							else
							{
								setupDeviceName("Would you like to give this device a name?");
							}
							loadAuroraData();
						}
						catch (StatusCodeException | HttpRequestException schre)
						{
							new TextDialog(Main.this,
									"An error occurred while connecting to the Aurora." +
									"Please try again.").setVisible(true);
						}
						canvas.setAurora(device);
					}
				}
			}, 0, 1000);
		}).start();
	}
	
	public void setupDeviceName(String message)
	{
		OptionDialog nameDeviceDialog = new OptionDialog(Main.this,
				message, "Yes", "No", new ActionListener()
				{
					@Override
					public void actionPerformed(ActionEvent e)
					{
						JButton okButton = (JButton)e.getSource();
						OptionDialog optionDialog =
								(OptionDialog)okButton.getFocusCycleRootAncestor();
							new SingleEntryDialog(Main.this, "Device Name", "Ok", new ActionListener()
								{
									@Override
									public void actionPerformed(ActionEvent e)
									{
										JButton okButton = (JButton)e.getSource();
										SingleEntryDialog entryDialog =
												(SingleEntryDialog)okButton.getFocusCycleRootAncestor();
										String name = entryDialog.getEntryField().getText();
										setDeviceName(device.getHostName(), name);
										lblTitle.setText("Connected to " + name);
										entryDialog.dispose();
										optionDialog.dispose();
									}
								})
							.setVisible(true);
					}
				},
				new ActionListener()
				{
					@Override
					public void actionPerformed(ActionEvent e)
					{
						JButton button = (JButton)e.getSource();
						OptionDialog thisDialog =
								(OptionDialog)button.getFocusCycleRootAncestor();
						thisDialog.dispose();
					}
				});
		nameDeviceDialog.setVisible(true);
	}
	
	private void loadDeviceName()
	{
		String deviceName = getDeviceName(device.getHostName());
		if (deviceName != null)
		{
			lblTitle.setText("Connected to " + deviceName);
		}
		else
		{
			lblTitle.setText("Connected to " + device.getName());
			setupDeviceName("It looks like you haven't set a name for your device yet. " +
				"Do you want to do this now?");
		}
	}
	
	private void setDeviceName(String ip, String name)
	{
		Map<String, Object> devices = getDevices();
		devices.put(ip, name);
		
		JSONObject json = new JSONObject(devices);
		PropertyManager manager = new PropertyManager(PROPERTIES_FILEPATH);
		manager.setProperty("devices", json.toString());
	}
	
	private String getDeviceName(String ip)
	{
		return (String)getDevices().get(ip);
	}
	
	private Map<String, Object> getDevices()
	{
		PropertyManager manager = new PropertyManager(PROPERTIES_FILEPATH);
		String devicesStr = manager.getProperty("devices");
		if (devicesStr != null)
		{
			JSONObject json = new JSONObject(devicesStr);
			return json.toMap();
		}
		return new HashMap<String, Object>();
	}
	
	private void initUI()
	{
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		setBounds(100, 100, DEFAULT_WINDOW_WIDTH, DEFAULT_WINDOW_HEIGHT);
		setSize(getUserWindowWidth(), getUserWindowHeight());
		setUndecorated(true);
		URL iconPath = getClass().getResource("resources/images/icon.png");
		setIconImage(new ImageIcon(iconPath).getImage());
		
		contentPane = new JPanel();
		contentPane.setBackground(Color.DARK_GRAY);
		contentPane.setBorder(new LineBorder(new Color(128, 128, 128), 3, true));
		setContentPane(contentPane);
		contentPane.setLayout(new MigLayout("", "[-27.00,grow][755.00,grow]",
				"[][680.00,growprio 105,grow][grow]"));
		
		JButton btnMenu = new MenuButton();
		contentPane.add(btnMenu, "flowx,cell 0 0,gapx 0 10");
		
		lblTitle = new JLabel("Not Connected");
		lblTitle.setAlignmentX(Component.CENTER_ALIGNMENT);
		lblTitle.setFont(new Font("Tahoma", Font.PLAIN, 20));
		lblTitle.setForeground(Color.WHITE);
		contentPane.add(lblTitle, "cell 0 0,growx");
		
		HideButton btnHide = new HideButton(this);
		contentPane.add(btnHide, "cell 1 0,alignx right,gapx 0 15");
		
		MaximizeButton btnMax = new MaximizeButton(this);
		contentPane.add(btnMax, "cell 1 0,alignx right,gapx 0 15");
		
		CloseButton btnClose = new CloseButton(this, JFrame.EXIT_ON_CLOSE);
		contentPane.add(btnClose, "cell 1 0,alignx right,gapx 0 15");
		
		scrlPaneRegEffects = new JScrollPane();
		scrlPaneRegEffects.getVerticalScrollBar().setUI(new ModernScrollBarUI());
		scrlPaneRegEffects.getHorizontalScrollBar().setUI(new ModernScrollBarUI());
		scrlPaneRegEffects.setBackground(Color.DARK_GRAY);
		scrlPaneRegEffects.setForeground(Color.WHITE);
		scrlPaneRegEffects.setBorder(new TitledBorder(new LineBorder(Color.GRAY),
				"Regular Effects", TitledBorder.LEFT, TitledBorder.TOP, null, Color.WHITE));
		((javax.swing.border.TitledBorder)scrlPaneRegEffects.getBorder())
			.setTitleFont(new Font("Tahoma", Font.BOLD, 22));
		contentPane.add(scrlPaneRegEffects, "cell 0 1,grow");
		
		regularEffectsList = new JList<String>(regularEffects);
		regularEffectsList.setBackground(Color.DARK_GRAY);
		regularEffectsList.setForeground(Color.WHITE);
		regularEffectsList.setFont(new Font("Tahoma", Font.PLAIN, 19));
		regularEffectsList.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				JList<String> list = (JList<String>)e.getSource();
				try
				{
					device.effects().setEffect(list.getSelectedValue());
					canvas.checkAuroraState();
					loadStateComponents();
				}
				catch (StatusCodeException sce)
				{
					new TextDialog(Main.this,
							"The requested action could not be completed. " +
							"Please try again.").setVisible(true);
				}
			}
		});
		LoadingSpinner regEffectsSpinner = new LoadingSpinner(Color.DARK_GRAY);
		scrlPaneRegEffects.setViewportView(regEffectsSpinner);
		
		canvas = new PanelCanvas(device);
		canvas.setLayout(new GridBagLayout());
		canvas.setBorder(new TitledBorder(new LineBorder(Color.GRAY),
				"Preview", TitledBorder.LEFT, TitledBorder.TOP, null, Color.WHITE));
		((javax.swing.border.TitledBorder)canvas.getBorder())
			.setTitleFont(new Font("Tahoma", Font.BOLD, 22));
		contentPane.add(canvas, "cell 1 1,grow");
		
		scrlPaneRhythEffects = new JScrollPane();
		scrlPaneRhythEffects.getVerticalScrollBar().setUI(new ModernScrollBarUI());
		scrlPaneRhythEffects.getHorizontalScrollBar().setUI(new ModernScrollBarUI());
		scrlPaneRhythEffects.setBorder(null);
		scrlPaneRhythEffects.setBackground(Color.DARK_GRAY);
		scrlPaneRhythEffects.setForeground(Color.WHITE);
		scrlPaneRhythEffects.setBorder(new TitledBorder(new LineBorder(Color.GRAY),
				"Rhythm Effects", TitledBorder.LEFT, TitledBorder.TOP, null, Color.WHITE));
		((javax.swing.border.TitledBorder)scrlPaneRhythEffects.getBorder())
			.setTitleFont(new Font("Tahoma", Font.BOLD, 22));
		contentPane.add(scrlPaneRhythEffects, "cell 0 2,grow");
		
		rhythmEffectsList = new JList<String>(rhythmEffects);
		rhythmEffectsList.setBackground(Color.DARK_GRAY);
		rhythmEffectsList.setForeground(Color.WHITE);
		rhythmEffectsList.setFont(new Font("Tahoma", Font.PLAIN, 19));
		rhythmEffectsList.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				JList<String> list = (JList<String>)e.getSource();
				try
				{
					device.effects().setEffect(list.getSelectedValue());
					canvas.checkAuroraState();
					loadStateComponents();
				}
				catch (StatusCodeException sce)
				{
					new TextDialog(Main.this,
							"The requested action could not be completed. " +
							"Please try again.").setVisible(true);
				}
			}
		});
		LoadingSpinner rhythEffectsSpinner = new LoadingSpinner(Color.DARK_GRAY);
		scrlPaneRhythEffects.setViewportView(rhythEffectsSpinner);
		
		UIManager.put("TabbedPane.contentBorderInsets",
				new InsetsUIResource(0, 0, 0, 0));
		UIManager.put("TabbedPane.focus", new Color(162, 184, 205));
		UIManager.put("TabbedPane.darkShadow", Color.DARK_GRAY);
		UIManager.put("TabbedPane.borderHightlightColor", Color.GRAY);
		UIManager.put("TabbedPane.light", Color.LIGHT_GRAY);
		UIManager.put("TabbedPane.selected", Color.DARK_GRAY);
		JTabbedPane editor = new JTabbedPane(JTabbedPane.TOP);
		editor.setForeground(Color.WHITE);
		editor.setBackground(Color.DARK_GRAY);
		editor.setBorder(new TitledBorder(new LineBorder(Color.GRAY, 1, true),
				"Edit", TitledBorder.LEFT, TitledBorder.TOP, null, Color.WHITE));
		((javax.swing.border.TitledBorder)editor.getBorder())
			.setTitleFont(new Font("Tahoma", Font.BOLD, 22));
		editor.addChangeListener(new ChangeListener()
		{
			@Override
			public void stateChanged(ChangeEvent e)
			{
				JTabbedPane editor = (JTabbedPane)e.getSource();
				if (editor.getSelectedComponent().equals(discoveryPanel))
				{
					EventQueue.invokeLater(() ->
					{
						discoveryPanel.addTopEffects(1, Main.this);
					});
				}
				
				// Safety to prevent preview from glitching
				EventQueue.invokeLater(() ->
				{
					canvas.repaint();
				});
			}
		});
		contentPane.add(editor, "cell 1 2,grow");
		
		JPanel informationPanel = new JPanel();
		informationPanel.setBorder(new LineBorder(Color.GRAY, 1, true));
		informationPanel.setBackground(Color.DARK_GRAY);
		editor.addTab("Control", null, informationPanel, null);
		informationPanel.setLayout(new MigLayout("", "[][428.00]", "[][][][][]"));
		
		JLabel lblOnOff = new JLabel("On/Off");
		lblOnOff.setFont(new Font("Tahoma", Font.PLAIN, 25));
		lblOnOff.setForeground(Color.WHITE);
		informationPanel.add(lblOnOff, "cell 0 0,aligny center");
		
		btnOnOff = new ModernToggleButton("Turn On");
		btnOnOff.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				JToggleButton btn = (JToggleButton)e.getSource();
				if (btn.getText().equals("Turn On"))
					btn.setText("Turn Off");
				else
					btn.setText("Turn On");
				
				try
				{
					device.state().toggleOn();
					canvas.toggleOn();
				}
				catch (HttpRequestException hre)
				{
					new TextDialog(Main.this,
							"Lost connection to the device. " +
							"Please try again.").setVisible(true);
				}
				catch (StatusCodeException sce)
				{
					new TextDialog(Main.this,
							"The requested action could not be completed. " +
							"Please try again.").setVisible(true);
				}
			}
		});
		informationPanel.add(btnOnOff, "cell 1 0");
		
		JLabel lblCurrentScene = new JLabel("Active Scene:");
		lblCurrentScene.setForeground(Color.WHITE);
		lblCurrentScene.setFont(new Font("Tahoma", Font.PLAIN, 25));
		informationPanel.add(lblCurrentScene, "cell 0 1");
		
		lblActiveScene = new JLabel("*None*");
		lblActiveScene.setForeground(Color.WHITE);
		lblActiveScene.setFont(new Font("Tahoma", Font.PLAIN, 25));
		informationPanel.add(lblActiveScene, "cell 1 1");
		
		JLabel lblBrightness = new JLabel("Brightness");
		lblBrightness.setForeground(Color.WHITE);
		lblBrightness.setFont(new Font("Tahoma", Font.PLAIN, 25));
		informationPanel.add(lblBrightness, "cell 0 2");
		
		brightnessSlider = new JSlider();
		brightnessSlider.setBackground(Color.DARK_GRAY);
		brightnessSlider.setUI(new ModernSliderUI(brightnessSlider,
				Color.GRAY, Color.DARK_GRAY, Color.DARK_GRAY));
		brightnessSlider.addChangeListener(new ChangeListener()
		{
			@Override
			public void stateChanged(ChangeEvent e)
			{
				if (!adjusting)
				{
					adjusting = true;
					new Thread(() ->
					{
						try
						{
							JSlider slider = (JSlider)e.getSource();
							if (slider.getValueIsAdjusting())
							{
								device.state().setBrightness(slider.getValue());
							}
							else
							{
								canvas.checkAuroraState();
								loadActiveScene();
							}
							adjusting = false;
						}
						catch (HttpRequestException hre)
						{
							new TextDialog(Main.this,
									"Lost connection to the device. " +
									"Please try again.").setVisible(true);
						}
						catch (StatusCodeException sce)
						{
							new TextDialog(Main.this,
									"The requested action could not be completed. " +
									"Please try again.").setVisible(true);
						}
					}).start();
				}
			}
		});
		informationPanel.add(brightnessSlider, "cell 1 2,growx");
		
		JLabel lblColorTemperature = new JLabel("Color Temperature");
		lblColorTemperature.setForeground(Color.WHITE);
		lblColorTemperature.setFont(new Font("Tahoma", Font.PLAIN, 25));
		informationPanel.add(lblColorTemperature, "cell 0 3,gapx 0 15");
		
		ctSlider = new JSlider();
		ctSlider.setMaximum(6400);
		ctSlider.setMinimum(1200);
		ctSlider.setBackground(Color.DARK_GRAY);
		ctSlider.setUI(new ModernSliderUI(ctSlider,
				Color.GRAY, Color.DARK_GRAY, Color.DARK_GRAY));
		ctSlider.addChangeListener(new ChangeListener()
		{
			@Override
			public void stateChanged(ChangeEvent e)
			{
				if (!adjusting)
				{
					adjusting = true;
					new Thread(() ->
					{
						try
						{
							JSlider slider = (JSlider)e.getSource();
							if (slider.getValueIsAdjusting())
							{
								device.state().setColorTemperature(slider.getValue());
							}
							else
							{
								canvas.checkAuroraState();
								loadActiveScene();
							}
						}
						catch (HttpRequestException hre)
						{
							new TextDialog(Main.this,
									"Lost connection to the device. " +
									"Please try again.").setVisible(true);
						}
						catch (StatusCodeException sce)
						{
							new TextDialog(Main.this,
									"The requested action could not be completed. " +
									"Please try again.").setVisible(true);
						}
						adjusting = false;
					}).start();
				}
			}
		});
		informationPanel.add(ctSlider, "cell 1 3,growx");
		
		JLabel lblSolidColor = new JLabel("Solid Color");
		lblSolidColor.setFont(new Font("Tahoma", Font.PLAIN, 25));
		lblSolidColor.setBackground(Color.DARK_GRAY);
		lblSolidColor.setForeground(Color.WHITE);
		informationPanel.add(lblSolidColor, "cell 0 4");
		
		JButton btnSetSolidColor = new ModernButton("Set Solid Color");
		btnSetSolidColor.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				JButton btn = (JButton)e.getSource();
				JFrame frame = (JFrame)btn.getFocusCycleRootAncestor();
				ColorPicker colorPicker = new ColorPicker(frame);
				colorPicker.setVisible(true);
				colorPicker.getColorWheel().addChangeListener(new ComponentChangeListener()
				{
					@Override
					public void stateChanged(ChangeEvent e)
					{
						if (!adjusting)
						{
							adjusting = true;
							new Thread(() ->
							{
								ColorWheel wheel = (ColorWheel)e.getSource();
								Color color = wheel.getColor();
								float[] hsb = new float[3];
								hsb = Color.RGBtoHSB(color.getRed(),
										color.getGreen(), color.getBlue(), hsb);
								
								try
								{
									device.state().setHue((int)(hsb[0]*360));
									device.state().setSaturation((int)(hsb[1]*100));
									device.state().setBrightness((int)(hsb[2]*100));
									loadStateComponents();
								}
								catch (HttpRequestException hre)
								{
									new TextDialog(Main.this,
											"Lost connection to the device. " +
											"Please try again.").setVisible(true);
								}
								catch (StatusCodeException sce)
								{
									new TextDialog(Main.this,
											"The requested action could not be completed. " +
											"Please try again.").setVisible(true);
								}
								
								canvas.setColor(color);
								adjusting = false;
							}).start();
						}
					}
				});
				colorPicker.getBrightnessSlider().addChangeListener(new ComponentChangeListener()
				{
					@Override
					public void stateChanged(ChangeEvent e)
					{
						if (!adjusting)
						{
							adjusting = true;
							new Thread(() ->
							{
								BrightnessSlider slider = (BrightnessSlider)e.getSource();
								int brightness = slider.getValue();
								try
								{
									device.state().setBrightness(brightness);
									int hue = device.state().getHue();
									int sat = device.state().getSaturation();
									canvas.setColor(Color.getHSBColor(hue/360f, sat/100f, brightness/100f));
									loadStateComponents();
								}
								catch (HttpRequestException hre)
								{
									new TextDialog(Main.this,
											"Lost connection to the device. " +
											"Please try again.").setVisible(true);
								}
								catch (StatusCodeException sce)
								{
									new TextDialog(Main.this, "The requested action could not be completed. " +
											"Please try again.").setVisible(true);
								}
								adjusting = false;
							}).start();
						}
					}
				});
			}
		});
		informationPanel.add(btnSetSolidColor, "cell 1 4");
		editor.setFont(new Font("Tahoma", Font.BOLD, 17));
		
		discoveryPanel = new DiscoveryPanel(device);
		editor.addTab("Discovery", null, discoveryPanel, null);
		
		ambilightPanel = new AmbilightPanel(canvas);
		editor.addTab("Ambient Lighting", null, ambilightPanel, null);
		
		SpotifyPanel spotifyPanel = new SpotifyPanel(device);
		editor.addTab("Spotify Visualizer", null, spotifyPanel, null);
		
		AuroraNullListener anl = new AuroraNullListener(20, null,
				canvas, discoveryPanel, ambilightPanel, spotifyPanel);
		anl.start();
		
		ComponentResizer cr = new ComponentResizer();
		cr.registerComponent(this);
		cr.setSnapSize(new Dimension(10, 10));
		cr.setMinimumSize(new Dimension(200, 200));
		
		WindowDragListener wdl = new MainWindowDragListener(50, canvas);
		addMouseListener(wdl);
		addMouseMotionListener(wdl);
		
		WindowOpeningListener wol = new WindowOpeningListener(this);
		addWindowListener(wol);
		
		addComponentListener(new ComponentAdapter()
		{
			@Override
			public void componentResized(ComponentEvent e)
			{
				PropertyManager manager = new PropertyManager(PROPERTIES_FILEPATH);
				manager.setProperty("windowWidth", getWidth());
				manager.setProperty("windowHeight", getHeight());
				
				if (canvas != null)
				{
					EventQueue.invokeLater(() ->
					{
						canvas.initCanvas();
						canvas.repaint();
					});
				}
			}
		});
		
		if (device != null)
		{
			EventQueue.invokeLater(new Runnable()
			{
				public void run()
				{
					loadAuroraData();
				}
			});
		}
	}
	
	public static void main(String[] args)
	{
		EventQueue.invokeLater(new Runnable()
		{
			public void run()
			{
				try
				{
					Main frame = new Main();
					frame.setVisible(true);
				}
				catch (Exception e)
				{
					e.printStackTrace();
				}
			}
		});
	}
}