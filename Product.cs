public class Product
{
	// Cosmos DB uses a string id for documents.
	public string id { get; set; }
	public int Id { get; set; }
	public string Type { get; set; }
	public string Brand { get; set; }
	public string Name { get; set; }
	public string Description { get; set; }
	public double Price { get; set; }

	public double DiscountedPrice { get; set; }
	// Additional Cosmos system properties are omitted for brevity.
}