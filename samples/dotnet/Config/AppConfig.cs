using System.Configuration;

namespace Config
{
    public class AppConfig
    {
        public required string EndpointUrl { get; set; }
        public required string DatabaseId { get; set; }
        public required string ContainerId { get; set; }
        public int TotalReads { get; set; }
        public int TotalQueries { get; set; }
        public int TotalCreates { get; set; }
        public int MaxReadConcurrency { get; set; }
        public int MaxQueryConcurrency { get; set; }
        public int MaxInsertConcurrency { get; set; }
        public int RunDurationInSecs { get; set; }

        public static AppConfig FromAppSettings()
        {
            return new AppConfig
            {
                EndpointUrl = ConfigurationManager.AppSettings["CosmosEndpoint"],
                DatabaseId = ConfigurationManager.AppSettings["CosmosDatabase"],
                ContainerId = ConfigurationManager.AppSettings["CosmosContainer"],
                TotalReads = int.Parse(ConfigurationManager.AppSettings["TotalReads"]),
                TotalQueries = int.Parse(ConfigurationManager.AppSettings["TotalQueries"]),
                TotalCreates = int.Parse(ConfigurationManager.AppSettings["TotalCreates"]),
                MaxReadConcurrency = int.Parse(ConfigurationManager.AppSettings["MaxReadConcurrency"]),
                MaxQueryConcurrency = int.Parse(ConfigurationManager.AppSettings["MaxQueryConcurrency"]),
                MaxInsertConcurrency = int.Parse(ConfigurationManager.AppSettings["MaxBulkInsertConcurrency"]),
                RunDurationInSecs = int.Parse(ConfigurationManager.AppSettings["RunDurationInSecs"])
            };
        }
    }
}