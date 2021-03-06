package app;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.SocketTimeoutException;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Map;
import java.util.Vector;

import javax.servlet.ServletException;
import javax.servlet.annotation.MultipartConfig;
import javax.servlet.annotation.WebInitParam;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.servlet.http.Part;

import org.apache.tomcat.util.http.fileupload.FileUtils;

@WebServlet(name = "SendMessage", urlPatterns = { "/index.html" }, initParams = {
		@WebInitParam(name = "upload_path", value = "files"), @WebInitParam(name = "db_user_name", value = "e1700698"),
		@WebInitParam(name = "db_password", value = "2ny4mn4F&qNY"),
		@WebInitParam(name = "db_name", value = "e1700698_jsp"),
		@WebInitParam(name = "db_url", value = "jdbc:mysql://mysql.cc.puv.fi:3306/"),
		@WebInitParam(name = "db_table_name", value = "messages_encrypted") })
@MultipartConfig(fileSizeThreshold = 1024 * 1024 * 10, // 10 MB
		maxFileSize = 1024 * 1024 * 50, // 50 MB
		maxRequestSize = 1024 * 1024 * 100 // 100 MB
)
public class SendMessage extends HttpServlet {
	private static final long serialVersionUID = 1L;

	Connection conn = null;
	Statement stmt = null;

	String uploadFilePath;

	private Vector<Message> messages = new Vector<>();

	SimpleDateFormat formatter = new SimpleDateFormat("dd/MM/yyyy");

	public void init() {
		uploadFilePath = this.getServletContext()
				.getRealPath(getServletConfig().getInitParameter("upload_path")) + File.separator;

		File fileSaveDir = new File(uploadFilePath);

		if (!fileSaveDir.exists()) {
			fileSaveDir.mkdirs();
		}
	}

	@Override
	protected void doPost(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {
		String dbTableName = getServletConfig().getInitParameter("db_table_name");

		String sender = request.getParameter("userName");
		String message = request.getParameter("message");

		Part filePart = request.getPart("fileName");

		InputStream inputStream = filePart.getInputStream();

		String date = formatter.format(new Date())
				.toString();

		try {
			String dbName = getServletConfig().getInitParameter("db_name");
			String dbUrl = getServletConfig().getInitParameter("db_url") + dbName;
			String dbUserName = getServletConfig().getInitParameter("db_user_name");
			String dbPassword = getServletConfig().getInitParameter("db_password");

			conn = DriverManager.getConnection(dbUrl, dbUserName, dbPassword);

			PreparedStatement ps = conn.prepareStatement(
					"insert into " + dbTableName + " values (AES_ENCRYPT('" + sender + "', '1234'), '" + message + "', '" + date + "', ?)");

			ps.setBlob(1, inputStream);

			int counter = ps.executeUpdate();

			if (counter == 0) {
				System.out.println("Upload was not successful");
				return;
			}

			File directory = new File(uploadFilePath);

			int filesLength = 0;
			if (directory.exists()) {
				filesLength = directory.list().length;
			}

			String fileId = String.format("%08d%n", filesLength)
					.replace("\n", "")
					.replace("\r", "");

			messages.add(new Message(sender, message, date, fileId));
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		response.sendRedirect("index.html");
	}

	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {
		String dbTableName = getServletConfig().getInitParameter("db_table_name");

		messages = new Vector<Message>();

		String query = "select CAST(AES_DECRYPT(sender, '1234') as CHAR(50)), message, date, file from " + dbTableName;
		ResultSet resultSet = null;
		ResultSetMetaData resultSetMeatData = null;

		try {
			String dbName = getServletConfig().getInitParameter("db_name");
			String dbUrl = getServletConfig().getInitParameter("db_url") + dbName;
			String dbUserName = getServletConfig().getInitParameter("db_user_name");
			String dbPassword = getServletConfig().getInitParameter("db_password");

			conn = DriverManager.getConnection(dbUrl, dbUserName, dbPassword);
			stmt = conn.createStatement();

			resultSet = stmt.executeQuery(query);
			resultSetMeatData = resultSet.getMetaData();
			int columns = resultSetMeatData.getColumnCount();

			File directory = new File(uploadFilePath);

			if (directory.exists()) {
				FileUtils.cleanDirectory(directory);
			}

			while (resultSet.next()) {
				Vector<String> messageContents = new Vector<String>();
				
				for (int i = 1; i <= columns - 1; i++) {
					if (resultSet.getObject(i) != null) {
						messageContents.add(resultSet.getObject(i)
								.toString());
					}
				}

				InputStream inputStream = resultSet.getBinaryStream(columns);

				byte[] buffer = new byte[inputStream.available()];

				inputStream.read(buffer);

				int filesLength = 0;
				if (directory.exists()) {
					filesLength = directory.list().length;
				}

				String fileId = String.format("%08d%n", filesLength)
						.replace("\n", "")
						.replace("\r", "");

				File fileObj = new File(uploadFilePath + fileId);

				OutputStream outStream = new FileOutputStream(fileObj);

				outStream.write(buffer);

				outStream.close();

				messages.add(
						new Message(messageContents.get(0), messageContents.get(1), messageContents.get(2), fileId));
			}

		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		String sender = request.getParameter("userName");
		String date = request.getParameter("date");

		response.setContentType("text/html");
		StringBuilder stringBuilder = new StringBuilder();
		PrintWriter out = response.getWriter();

		stringBuilder.append("<html>");
		stringBuilder.append("<head>");
		stringBuilder.append("<title>Forum</title>");
		stringBuilder.append("<link rel='stylesheet' type='text/css' href='styles.css'/>");
		stringBuilder.append("</head>");
		stringBuilder.append("<body>");

		if (date != null && sender != null && date.length() == 0 && sender.length() == 0) {
			stringBuilder.append("<h1>You haven't provided parameters for the search</h1>");
			stringBuilder.append("</body>");
			stringBuilder.append("</html>");
			out.println(stringBuilder.toString());
			out.close();
		} else {
			stringBuilder.append("<h1>POST</h1>");
			stringBuilder.append("<form action='index.html' method='POST' enctype=\"multipart/form-data\">");
			stringBuilder.append("<table>");
			stringBuilder.append("<tr>");
			stringBuilder.append("<td>User Name</td>");
			stringBuilder.append("<td><input type='text' size='40' name='userName' required></td>");
			stringBuilder.append("</tr>");
			stringBuilder.append("<tr>");
			stringBuilder.append("<td>Message</td>");
			stringBuilder.append("<td><textarea size='40' name='message' required></textarea></td>");
			stringBuilder.append("</tr>");
			stringBuilder.append("<tr>");
			stringBuilder.append("<td>Images</td>");
			stringBuilder.append("<td>");
			stringBuilder.append("<input type=\"file\" name=\"fileName\">");
			stringBuilder.append("</td>");
			stringBuilder.append("</tr>");
			stringBuilder.append("<td><button type='submit' name='submit' value='submit'>send</button></td>");
			stringBuilder.append("</tr>");
			stringBuilder.append("</table>");
			stringBuilder.append("</form>");

			stringBuilder.append("<h1>GET</h1>");
			stringBuilder.append("<form action='index.html' method='GET'>");
			stringBuilder.append("<table>");
			stringBuilder.append("<tr>");
			stringBuilder.append("<td>User Name</td>");
			stringBuilder.append("<td><input type='text' size='40' name='userName'></td>");
			stringBuilder.append("</tr>");
			stringBuilder.append("<tr>");
			stringBuilder.append("<td>Date</td>");
			stringBuilder.append("<td><input type='date' size='40' name='date'></td>");
			stringBuilder.append("</tr>");
			stringBuilder.append("<tr>");
			stringBuilder.append("<td></td>");
			stringBuilder.append("<td><input type='submit' name='submit' VALUE='Send'> </td>");
			stringBuilder.append("</tr>");
			stringBuilder.append("</table>");
			stringBuilder.append("</form>");

			boolean isSameDate = false;
			for (Message messageItem : messages) {
				if (date != null) {
					SimpleDateFormat htmlDateFormatter = new SimpleDateFormat("yyyy-MM-dd");
					try {
						Date dateObj = htmlDateFormatter.parse(date);
						String parsed = formatter.format(dateObj);
						isSameDate = messageItem.getDate()
								.equals(parsed);
					} catch (ParseException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
				if (sender == null && date == null || isSameDate && sender.length() > 0 && messageItem.getSender()
						.equals(sender) || date.length() == 0 && sender.length() > 0 && messageItem.getSender()
								.equals(sender)
						|| sender.length() == 0 && isSameDate) {
					stringBuilder.append("<table class='forumTable'>");
					stringBuilder.append("<tbody class='forumTableBody'>");
					stringBuilder.append("<tr>");
					stringBuilder.append("<td class='tableItem'>");
					stringBuilder.append(messageItem.getSender());
					stringBuilder.append("</td>");
					stringBuilder.append("<td class='tableItem'>");
					stringBuilder.append(messageItem.getDate());
					stringBuilder.append("</td>");
					stringBuilder.append("</tr>");
					stringBuilder.append("<tr>");
					stringBuilder.append("<td class='tableItem'>");
					stringBuilder.append(messageItem.getMessage());
					stringBuilder.append("</td>");
					stringBuilder.append("</tr>");
					stringBuilder.append("<tr>");
					stringBuilder.append("<td colspan='4' class='tableItem'>");
					stringBuilder.append("<img src='/Assignment6_3/files/" + messageItem.getFileName() + "' width='400px'/>");
					stringBuilder.append("</td>");
					stringBuilder.append("</tr>");
					stringBuilder.append("</tbody>");
					stringBuilder.append("</table>");
				}
			}
			stringBuilder.append("</body>");
			stringBuilder.append("</html>");
			out.println(stringBuilder.toString());
			out.close();
		}
	}
}