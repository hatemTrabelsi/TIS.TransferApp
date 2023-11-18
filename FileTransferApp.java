import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.border.Border;
import java.io.*;
import java.nio.file.*; 
import java.util.*;
import java.util.List;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import javax.crypto.*;
import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.time.LocalDateTime;
import java.security.NoSuchAlgorithmException;

public class FileTransferApp extends JFrame {
  private JTextField selectedFilePathTextField;
  private SecretKey myKey;
  private JLabel statusLabel;
  private JButton encryptAndTransferButton;
  private JButton decryptAndSaveButton;
  private JButton chooseFileButton;
  private JButton serverButton;
  private boolean isServerRunning = false;
  private JButton connectButton;
  private ServerSocket serverSocket;
  private Socket clientSocket; 
  private JProgressBar progressBar;
  private JTextField ipTextField; 
  private Integer i = 0;
  public FileTransferApp() {
    setTitle("File Transfer App");
    setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    setLayout(new FlowLayout());
    statusLabel = new JLabel();
    selectedFilePathTextField = new JTextField(20);
    selectedFilePathTextField.setEditable(false);
    add(selectedFilePathTextField);

    chooseFileButton = new JButton("Choose File");
    chooseFileButton.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
            JFileChooser fileChooser = new JFileChooser();
            int returnValue = fileChooser.showOpenDialog(null);
            if (returnValue == JFileChooser.APPROVE_OPTION) {
                selectedFilePathTextField.setText(fileChooser.getSelectedFile().getAbsolutePath());
            }
        }
    });
    add(chooseFileButton);

    ipTextField = new JTextField(20);
    add(ipTextField);

    connectButton = new JButton("Connect");
    connectButton.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
            connectToServer();
        }
    });
    add(connectButton);

    encryptAndTransferButton = new JButton("Encrypt and Transfer");
    encryptAndTransferButton.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
            String sourceFilePath = selectedFilePathTextField.getText();
          if (sourceFilePath != null && !sourceFilePath.isEmpty()) {
              try {
                  String encryptedFilePath = encryptFile(sourceFilePath);
                  transferEncryptedFile(encryptedFilePath);
                  JOptionPane.showMessageDialog(null, "File encrypted and transferred successfully!");
              } catch (Exception ex) {
                  JOptionPane.showMessageDialog(null, "Error encrypting and transferring the file: " + ex.getMessage());
              }
          } else {
              JOptionPane.showMessageDialog(null, "Please choose a file to encrypt and transfer.");
          }
        }
    });
    add(encryptAndTransferButton);

    decryptAndSaveButton = new JButton("Decrypt and Save");
    decryptAndSaveButton.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
            decryptAndSave();
        }
    });
    add(decryptAndSaveButton);
  
    statusLabel.setSize(200, 20);
    serverButton = new JButton("Start Server");
    serverButton.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
            toggleServer();
        }
    });
    add(serverButton);   
    progressBar = new JProgressBar(0, 100);
    progressBar.setStringPainted(true);
    progressBar.setVisible(false);
    add(progressBar);

    add(statusLabel);
    setSize(400, 200);
    setLocationRelativeTo(null);
    setVisible(true);
  }

  

  private void transferEncryptedFile(String encryptedFilePath) {
    SwingWorker<Void, Integer> worker = new SwingWorker<Void, Integer>() {
        @Override
        protected Void doInBackground() throws Exception {
            try {
                progressBar.setVisible(true);
                String ip = ipTextField.getText();
                Socket socket = new Socket(ip, 12345);
                DataOutputStream dos = new DataOutputStream(socket.getOutputStream());

                dos.writeUTF("ENCRYPTED_FILE");

                File fileToSend = new File(encryptedFilePath);
                dos.writeUTF(fileToSend.getName());
                dos.writeLong(fileToSend.length());

                byte[] buffer = new byte[8192];
                int bytesRead;
                long totalBytesRead = 0;
                long fileSize = fileToSend.length();

                try (InputStream fileInputStream = new FileInputStream(fileToSend)) {
                    while ((bytesRead = fileInputStream.read(buffer)) != -1) {
                        dos.write(buffer, 0, bytesRead);
                        totalBytesRead += bytesRead;

                        int progress = (int) ((totalBytesRead * 100) / fileSize);
                        publish(progress);
                        try {
                          // Pause for 3 seconds
                          Thread.sleep(500);
                          } catch (InterruptedException e) {
                              e.printStackTrace();
                          }
                    }
                }

                System.out.println("File sent to server successfully.");

                // Close the connection
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }

            return null;
        } 
        @Override
        protected void process(List<Integer> chunks) {
            int latestProgress = chunks.get(chunks.size() - 1); 
            progressBar.setValue(latestProgress);
        }

        @Override
        protected void done() {
            progressBar.setValue(100);
            statusLabel.setText("File transfer complete"); 
            progressBar.setValue(0);
            progressBar.setVisible(false);
        }
    };

    worker.execute();
}

  public String encryptFile(String sourceFilePath) throws Exception {
    String encryptedFilePath = sourceFilePath + ".encrypted";
    File sourceFile = new File(sourceFilePath);
    File encryptedFile = new File(encryptedFilePath);

    Cipher cipher = Cipher.getInstance("AES");
    //myKey = AESKeyGenerator.generateAESKey();
    SecretKeySpec secretKeySpec = new SecretKeySpec(myKey.getEncoded(), "AES");
    cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec);

    try (InputStream inputStream = new FileInputStream(sourceFile);
        OutputStream outputStream = new CipherOutputStream(new FileOutputStream(encryptedFile), cipher)) {
      byte[] buffer = new byte[8192];
      int bytesRead;
      while ((bytesRead = inputStream.read(buffer)) != -1) {
        outputStream.write(buffer, 0, bytesRead);
      }
    }

    return encryptedFilePath;
  } 

  // Existing code...
 public String decryptFile(String encryptedFilePath) throws Exception {
    String decryptedFilePath = encryptedFilePath.replace(".encrypted", ".decrypted");
    File encryptedFile = new File(encryptedFilePath);
    File decryptedFile = new File(decryptedFilePath);

    Cipher cipher = Cipher.getInstance("AES");
    System.out.println("decrypting with key : " + Arrays.toString(myKey.getEncoded())); 
    SecretKeySpec secretKeySpec = new SecretKeySpec(myKey.getEncoded(), "AES");
    cipher.init(Cipher.DECRYPT_MODE, secretKeySpec);

    try (InputStream inputStream = new FileInputStream(encryptedFile);
         OutputStream outputStream = new FileOutputStream(decryptedFile)) {
        CipherInputStream cipherInputStream = new CipherInputStream(inputStream, cipher);

        byte[] buffer = new byte[8192];
        int bytesRead;
        while ((bytesRead = cipherInputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, bytesRead);
        }
    }

    return decryptedFilePath;
}

  public void saveFile(String filePath) {
    try {
      File sourceFile = new File(filePath);
      File savedFile = new File("saved_" + sourceFile.getName());
      Files.copy(sourceFile.toPath(), savedFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
      JOptionPane.showMessageDialog(null, "File saved successfully!");
    } catch (IOException e) {
      JOptionPane.showMessageDialog(null, "Error saving the file: " + e.getMessage());
    }
  }

  private void decryptAndSave() {
      String encryptedFilePath = selectedFilePathTextField.getText();
      if (encryptedFilePath != null && !encryptedFilePath.isEmpty()) {
          try {
              String decryptedFilePath = decryptFile(encryptedFilePath);
              saveFile(decryptedFilePath);
              JOptionPane.showMessageDialog(null, "File decrypted and saved successfully!");
          } catch (Exception ex) {
              JOptionPane.showMessageDialog(null, "Error decrypting and saving the file: " + ex.getMessage());
          }
      } else {
          JOptionPane.showMessageDialog(null, "Please choose an encrypted file to decrypt and save.");
      }
  }

  private void toggleServer() {
      if (!isServerRunning) {
          startServer();
          serverButton.setText("Stop Server");
      } else {
          stopServer();
          serverButton.setText("Start Server");
      }
  }

  private void startServer(){
    try {
        serverSocket = new ServerSocket(12345);
        isServerRunning = true;
        try {
            InetAddress ipAddress = InetAddress.getLocalHost();
            statusLabel.setText("Status: Running (Waiting for connection) on IP adress : "+ ipAddress.getHostAddress()); 
        } catch (UnknownHostException e) {
            e.printStackTrace();
        } 

        new Thread(() -> {
            while (isServerRunning) {
                try {
                    // Accept client connection
                    clientSocket = serverSocket.accept();
                    statusLabel.setText("Status: Connection established with " + clientSocket.getInetAddress());
                    System.out.println(clientSocket.getInetAddress()); 
                    // Create input and output streams for the client
                    DataInputStream dis = new DataInputStream(clientSocket.getInputStream());
                    DataOutputStream dos = new DataOutputStream(clientSocket.getOutputStream());
                    // Send the key to the client
                    dos.writeUTF("Welcome to the server!"); 
                    if(i==0)
                    try {
                        myKey = AESKeyGenerator.generateAESKey();
                        sendKeyToClient(myKey, dos);
                        System.out.println("Sending key from server: " + Arrays.toString(myKey.getEncoded()));
                        i++;
                    } catch (NoSuchAlgorithmException e) {
                        e.printStackTrace(); // or handle the exception in an appropriate way
                    }

                    

                    // Receive a message from the client
                    String clientMessage = dis.readUTF();
                    System.out.println("Client says: " + clientMessage);

                    

                    if ("ENCRYPTED_FILE".equals(clientMessage)) {
                        // Receive the encrypted file from the client
                        receiveFileFromClient(dis);
                    }

                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            }

            // Server has stopped
            try {
                serverSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }

            statusLabel.setText("Status: Not Running");
        }).start();
    } catch (IOException e) {
        e.printStackTrace();
    }
}

private void sendKeyToClient(SecretKey key, OutputStream outputStream) {
    try {
        byte[] keyBytes = key.getEncoded();
        DataOutputStream dos = new DataOutputStream(outputStream);
        dos.writeInt(keyBytes.length);
        dos.write(keyBytes);
        dos.flush();
    } catch (IOException e) {
        e.printStackTrace();
    }
}

private void receiveFileFromClient(DataInputStream dis) {
    try {
        String saveDirectory = System.getProperty("user.dir");
        // Replace this with your file receiving logic
        String fileName = dis.readUTF();
        long fileSize = dis.readLong();

        File receivedFile = new File(saveDirectory, "received_" + fileName);
        FileOutputStream fileOutputStream = new FileOutputStream(receivedFile);
        System.out.println("File is here: " + saveDirectory + File.separator + "received_" + fileName);

        byte[] buffer = new byte[8192];
        int bytesRead;

        while ((bytesRead = dis.read(buffer)) != -1) {
            fileOutputStream.write(buffer, 0, bytesRead);
        }

        fileOutputStream.close();

        System.out.println("File received from client and saved to: " + receivedFile.getAbsolutePath());

    } catch (IOException e) {
        e.printStackTrace();
    }
}

  private void stopServer() {
    try {
        if (clientSocket != null && !clientSocket.isClosed()) {
            clientSocket.close();
        }
        if (serverSocket != null && !serverSocket.isClosed()) {
            serverSocket.close();
        }
        isServerRunning = false;
        
        // Introduce a delay to allow the server thread to update the label
        Thread.sleep(500);

        statusLabel.setText("Status: Not Running");
    } catch (IOException | InterruptedException e) {
        e.printStackTrace();
    }
}

private void connectToServer() {
    try {
        String ip = ipTextField.getText();
        Socket socket = new Socket(ip, 12345); // Change "localhost" to the server's IP if running on a different machine
        DataInputStream dis = new DataInputStream(socket.getInputStream());
        DataOutputStream dos = new DataOutputStream(socket.getOutputStream());

        // Receive the welcome message from the server
        String welcomeMessage = dis.readUTF();
        System.out.println("Server says: " + welcomeMessage);

        // Send a message to the server
        dos.writeUTF("Hello Server!");
        // Receiving key from the server 
         
        // Use SwingWorker to perform file receiving in the background
        SwingWorker<Void, Void> worker = new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                // Receiving key from the server
                if(i==0)
                {
                  myKey = receiveKeyFromServer(socket.getInputStream());
                  System.out.println("Receiving key from server: " + Arrays.toString(myKey.getEncoded())); 
                  statusLabel.setText("You are connected to the server on "+ip);
                  i++;
                }
                
                // Continue with your file transfer logic or other communication
                receiveEncryptedFile(socket, dis);

                return null;
            }

            @Override
            protected void done() {
                try {
                    get(); // Retrieve the result of doInBackground, if necessary
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    try {
                        socket.close(); // Close the connection
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        };

        worker.execute(); // Start the SwingWorker
    } catch (IOException e) {
        e.printStackTrace();
    }
}

private SecretKey receiveKeyFromServer(InputStream inputStream) {
    try {
        DataInputStream dis = new DataInputStream(inputStream);
        int keyLength = dis.readInt();
        byte[] keyBytes = new byte[keyLength];
        dis.readFully(keyBytes);
        return new SecretKeySpec(keyBytes, "AES");
    } catch (IOException e) {
        e.printStackTrace();
        return null;
    }
}

  private void receiveEncryptedFile(Socket socket, DataInputStream dis) {
      try {
        // Replace the following logic with your decryption logic
        // Example: CipherInputStream cis = new CipherInputStream(dis, cipher);
        String saveDirectory = System.getProperty("user.dir");

        // Receive the file name from the server
        String fileName = dis.readUTF();
        System.out.println("File received in " + saveDirectory + File.separator + "received_" + fileName);
        // Create a file on the client-side
        File receivedFile = new File(saveDirectory, "received_" + fileName);
        FileOutputStream fileOutputStream = new FileOutputStream(receivedFile);

        // Receive the encrypted file content
        byte[] buffer = new byte[8192];
        int bytesRead;

        while ((bytesRead = dis.read(buffer)) != -1) {
            fileOutputStream.write(buffer, 0, bytesRead);
        }

        fileOutputStream.close();
        SwingUtilities.invokeLater(() -> {
            statusLabel.setText("Status: File received successfully and saved to " + receivedFile.getAbsolutePath());
        });
    } catch (IOException e) {
        e.printStackTrace();
    }
  } 

  private void receiveFile() {
    try {
      InputStream inputStream = clientSocket.getInputStream();
      FileOutputStream fileOutputStream = new FileOutputStream("received_file.txt");

      byte[] buffer = new byte[8192];
      int bytesRead;

      while ((bytesRead = inputStream.read(buffer)) != -1) {
        fileOutputStream.write(buffer, 0, bytesRead);
      }

      statusLabel.setText("Status: File received successfully.");

      // Log file received information
      System.out.println("File received successfully at " + LocalDateTime.now());

      fileOutputStream.close();
      inputStream.close();
      clientSocket.close(); 
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  public static void main(String[] args) {
    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        new FileTransferApp();
      }
    });
  }
}