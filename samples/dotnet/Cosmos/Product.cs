using System.Collections.Generic;
using Bogus;

namespace Cosmos
{
	public class Product
	{
		public string id { get; set; }
		public int Id { get; set; }
		public string Type { get; set; }
		public string Brand { get; set; }
		public string Name { get; set; }
		public string Description { get; set; }
		public double Price { get; set; }


		public static List<string> TypeList { get; } = new List<string> {
		"Accessories",
		"Apparel",
		"Bags",
		"Climbing",
		"Cycling",
		"Electronics",
		"Footwear",
		"Home",
		"Jackets",
		"Navigation",
		"Ski/boarding",
		"Trekking"
	};

		public static Product GenerateProduct(int id)
		{
			var faker = new Bogus.Faker();
			return new Product
			{
				id = id.ToString(),
				Id = id,
				Type = faker.PickRandom(TypeList),
				Brand = faker.Company.CompanyName(),
				Name = faker.Commerce.ProductName(),
				Description = faker.Lorem.Sentence(),
				Price = faker.Random.Number(50, 500)
			};
		}
	}
}