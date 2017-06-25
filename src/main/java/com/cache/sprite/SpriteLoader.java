package com.cache.sprite;

import com.cache.CacheLoader;
import com.cache.sprite.handler.SpriteHandler;
import com.cache.sprite.impl.Cached;
import com.cache.sprite.impl.Raw;
import com.cache.sprite.util.BeanType;
import com.configuration.Constants;
import com.configuration.util.Environment;
import com.logging.Logger;
import com.resource.FileOperations;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.DefaultTableModel;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.logging.Level;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * @author Daniel
 */
public class SpriteLoader extends CacheLoader {

    private SpriteLoader() {
    }

    public static SpriteLoader getInstance() {
        return InstanceHolder.instance != null ? InstanceHolder.instance : (InstanceHolder.instance = new SpriteLoader());
    }

    public void reload() {
        InstanceHolder.instance = new SpriteLoader();
    }

    @Override
    protected void load() {
        SpriteHandler.refreshBeans();
        buildCachedList();
        buildRawList();
    }

    private void buildRawList() {
        listRaw(Constants.RAW_SPRITES_DIRECTORY);
    }

    private void listRaw(File file) {
        if (file.isDirectory()) {
            final File[] files = file.listFiles();
            if (files != null) {
                final List<File> list = Arrays.asList(files);
                if (!list.isEmpty()) {
                    for (File child : list) {
                        listRaw(child);
                    }
                }
            }
        } else {
            if (fits(file)) {
                final Raw raw = new Raw(file);
                try {
                    raw.setImage(byteArrayToImage(raw.getBytes()));
                    SpriteHandler.submit(raw);
                } catch (IOException ex) {
                    Logger.log(SpriteLoader.class, Level.WARNING, String.format("Error getting Image from '%s'", raw.getFile().getName()), ex);
                }
            }
        }
    }

    public void dumpCachedList() {
        final File directory = new File(Constants.DUMPED_SPRITES_DIRECTORY, String.valueOf(new Date()).replace(":", "_"));
        Environment.create(directory, true);
        for (Bean bean : SpriteHandler.getBeanList(BeanType.CACHED)) {
            final Cached cached = (Cached) bean;
            cached.saveImage(new File(directory, String.format("%s.png", cached.getId())));
        }
    }

    @SuppressWarnings("all")
    public void removeBean(Bean bean) {
        if (bean.getBeanType().equals(BeanType.CACHED)) {
            ((Cached) bean).saveImage(new File(Constants.REMOVED_CACHED_SPRITES_DIRECTORY, String.format("%d-%s.png", ((Cached) bean).getId(), String.valueOf(new Date()).replace(":", "_"))));
        } else if (bean.getBeanType().equals(BeanType.RAW)) {
            ((Raw) bean).getFile().renameTo(new File(Constants.REMOVED_RAW_SPRITES_DIRECTORY, String.format("[%s]%s", String.valueOf(new Date()).replace(":", "_"), ((Raw) bean).getFile().getName())));
        }
    }

    public void pack() {
        writeIndexFile();
        writeDataFile();
    }

    private void writeIndexFile() {
        DataOutputStream outputStream;
        try {
            outputStream = new DataOutputStream(new GZIPOutputStream(new FileOutputStream(Constants.CACHED_SPRITES_INDEX_FILE)));
            final List<Bean> list = SpriteHandler.getBeanList(BeanType.CACHED);
            outputStream.writeInt(list.size());
            for (Bean bean : list) {
                final Cached cached = (Cached) bean;
                outputStream.writeInt(cached.getId());
                outputStream.writeInt(cached.getBytes().length);
            }
            outputStream.flush();
            outputStream.close();
        } catch (Exception ex) {
            Logger.log(SpriteLoader.class, Level.WARNING, "Error Writing Index File", ex);
        }
    }

    private void writeDataFile() {
        DataOutputStream outputStream;
        try {
            outputStream = new DataOutputStream(new GZIPOutputStream(new FileOutputStream(Constants.CACHED_SPRITES_DATA_FILE)));
            for (Bean bean : SpriteHandler.getBeanList(BeanType.CACHED)) {
                final Cached cached = (Cached) bean;
                if(cached.getId() != -1) {
                    outputStream.writeByte(1);
                    outputStream.writeShort(cached.getId());
                }
                if(cached.getName() != null) {
                    outputStream.writeByte(2);
                    outputStream.writeUTF(cached.getName());
                }
                if(cached.getX() != 0) {
                    outputStream.writeByte(3);
                    outputStream.writeShort(cached.getX());
                }
                if(cached.getY() != 0) {
                    outputStream.writeByte(4);
                    outputStream.writeShort(cached.getY());
                }
                if(cached.getBytes() != null && cached.getBytes().length > 0) {
                    outputStream.writeByte(5);
                    outputStream.write(cached.getBytes());
                }

                outputStream.writeByte(0);
            }
            outputStream.flush();
            outputStream.close();
        } catch (Exception ex) {
            Logger.log(SpriteLoader.class, Level.WARNING, "Error Writing Data File", ex);
        }
    }

    private BufferedImage byteArrayToImage(byte[] data) throws IOException {
        return ImageIO.read(new ByteArrayInputStream(data));
    }

    @SuppressWarnings("unchecked")
    public void setRawListModel(JList list) {
        AbstractListModel listModel = new AbstractListModel() {
            @Override
            public int getSize() {
                return SpriteHandler.getBeanList(BeanType.RAW).size();
            }

            @Override
            public Object getElementAt(int index) {
                return SpriteHandler.getBeanList(BeanType.RAW).get(index);
            }
        };
        list.setModel(listModel);
    }

    @SuppressWarnings("unchecked")
    public void setCachedListModel(JList<?> list) {
        AbstractListModel listModel = new AbstractListModel<Object>() {
            @Override
            public int getSize() {
                return SpriteHandler.getBeanList(BeanType.CACHED).size();
            }

            @Override
            public Object getElementAt(int index) {
                return SpriteHandler.getCachedBean(index);
            }
        };
        list.setModel(listModel);
    }

    public void setTableModel(JTable table, final Bean bean) {
        if (bean.getBeanType().equals(BeanType.CACHED)) {
            final Cached cached = (Cached) bean;
            final DefaultTableModel model = new DefaultTableModel(
                    new Object[][]{
                            {Constants.ALGORITHM, cached.getHash()},
                            {"Name", cached.getName() != null ? cached.getName() : ""},
                            {"X", cached.getX()},
                            {"Y", cached.getY()},
                            {"Width", cached.getImage().getWidth()},
                            {"Height", cached.getImage().getHeight()}
                    },
                    new String[]{
                            "Variable", "Value"
                    }
            ) {
                /**
				 * 
				 */
				private static final long serialVersionUID = 7388655437255511908L;
				final Class[] types = new Class[]{
                        String.class, Object.class
                };
                final boolean[][] canEdit = new boolean[][]{
                        {false, false},
                        {false, true, true, true, false, false},
                };

                public Class getColumnClass(int columnIndex) {
                    return types[columnIndex];
                }

                public boolean isCellEditable(int rowIndex, int columnIndex) {
                    return canEdit[columnIndex][rowIndex];
                }
            };
            model.addTableModelListener(new TableModelListener() {
                @Override
                public void tableChanged(TableModelEvent event) {
                    final int column = event.getColumn();
                    final int row = event.getFirstRow();
                    if (event.getType() == TableModelEvent.UPDATE) {
                        if (column == 1) {
                            final String value = String.valueOf(model.getValueAt(row, column));
                            if (row == 1) {
                                cached.setName(String.valueOf(model.getValueAt(row, column)));
                            } else if (row == 2) {
                                if (value.matches("[\\d]{0,10}")) {
                                    cached.setX(Integer.parseInt(value));
                                } else {
                                    model.setValueAt(cached.getX(), row, column);
                                }
                            } else if (row == 3) {
                                if (value.matches("[\\d]{0,10}")) {
                                    cached.setY(Integer.parseInt(value));
                                } else {
                                    model.setValueAt(cached.getY(), row, column);
                                }
                            }
                        }
                    }
                }
            });
            table.setModel(model);
        } else {
            final Raw raw = (Raw) bean;
            final DefaultTableModel model = new DefaultTableModel(
                    new Object[][]{
                            {Constants.ALGORITHM, raw.getHash()},
                            {"Path", raw.getFile().getParentFile().getAbsolutePath()},
                            {"Name", raw.getFile().getName()},
                            {"Width", raw.getImage().getWidth()},
                            {"Height", raw.getImage().getHeight()}
                    },
                    new String[]{
                            "Variable", "Value"
                    }
            ) {

                /**
				 * 
				 */
				private static final long serialVersionUID = -1920081595396392874L;

				public boolean isCellEditable(int rowIndex, int columnIndex) {
                    return false;
                }

            };
            table.setModel(model);
        }
    }

    private boolean fits(File file) {
        return file.getName().endsWith("png")
                || file.getName().endsWith("jpg")
                || file.getName().endsWith("jpeg")
                || file.getName().endsWith("PNG")
                || file.getName().endsWith("JPG")
                || file.getName().endsWith("JPEG");
    }

    private void buildCachedList() {
        try {
            final DataInputStream indexStream = new DataInputStream(new GZIPInputStream(new ByteArrayInputStream(FileOperations.readFile(Constants.CACHED_SPRITES_INDEX_FILE))));
            final DataInputStream dataStream = new DataInputStream(new GZIPInputStream(new ByteArrayInputStream(FileOperations.readFile(Constants.CACHED_SPRITES_DATA_FILE))));
            int size = indexStream.readInt();
            for (int index = 0; index < size; index++) {
                indexStream.readInt();
                final Cached cached = new Cached();
                cached.readValues(indexStream, dataStream);
                cached.setImage(byteArrayToImage(cached.getBytes()));
                SpriteHandler.submit(cached);
            }
            indexStream.close();
            dataStream.close();
        } catch (Exception ex) {
            Logger.log(SpriteLoader.class, Level.WARNING, "Error Building Cached List", ex);
        }
    }

    private static class InstanceHolder {
        private static SpriteLoader instance;
    }

}
