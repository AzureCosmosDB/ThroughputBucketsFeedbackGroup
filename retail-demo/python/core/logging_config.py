import logging

logger = logging.getLogger(__name__)

# configure logger to log on console
logging.basicConfig(
    level=logging.INFO, format="%(levelname)s - %(message)s",
    handlers=[
        logging.StreamHandler()  # Output to console (stdout)
    ]
)

# Suppress Azure SDK detailed logging (HTTP requests/responses)
logging.getLogger('azure.cosmos').setLevel(logging.WARNING)
logging.getLogger('azure.core').setLevel(logging.WARNING)
logging.getLogger('azure').setLevel(logging.WARNING)
logging.getLogger('azure.core.pipeline').setLevel(logging.WARNING)

def get_logger():
    return logger