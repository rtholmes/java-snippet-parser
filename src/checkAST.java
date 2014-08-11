public class checkAST{
	public void foo(){
		String query = "select COF_NAME, PRICE from COFFEES";
		try (Statement stmt = con.createStatement()) {
			ResultSet rs = stmt.executeQuery(query);
			while (rs.next()) {
				String coffeeName = rs.getString("COF_NAME");
				float price = rs.getFloat("PRICE");
				System.out.println(coffeeName + ", "  + price);
			}
		}

	}
}