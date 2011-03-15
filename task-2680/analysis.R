# Read descriptors.csv.
cat("Reading descriptors.csv.\n")
data <- read.csv("descriptors.csv", stringsAsFactors = FALSE)
cat("Read", length(data$fingerprint), "rows from descriptors.csv.\n")

# We're interested in bridge stats.  Let's filter out all descriptors that
# don't have any bridge stats.
data <- data[!is.na(data$bridgestatsend), ]
cat(length(data$fingerprint), "of these rows have bridge stats.\n")

# Sort data first by bridge fingeprint, then by bridge stats interval end.
data <- data[order(data$fingerprint, data$bridgestatsend), ]
cat("Here are the first 10 rows, sorted by fingerprint and bridge",
    "stats\ninterval end, and only displaying German and French users:\n")
data[1:10, c("fingerprint", "bridgestatsend", "de", "fr")]

# Looks good, but we should exclude all bridges that have been seen as
# relays, or they will skew our results.  Read relays.csv.
cat("Reading relays.csv\n")
relays <- read.csv("relays.csv", stringsAsFactors = FALSE)
cat("Read", length(relays$fingerprint), "rows from relays.csv.\n")

# Filter out all descriptors of bridges that have been seen as relays.
cat("Filtering out bridges that have been seen as relays.\n")
data <- data[!data$fingerprint %in% relays$fingerprint, ]
cat(length(data$fingerprint), "descriptors remain.  Again, here are the",
    "first 10 rows, sorted by\nfingerprint and bridge stats interval",
    "end, and only displaying German\nand French users:\n")
data[1:10, c("fingerprint", "bridgestatsend", "de", "fr")]

# And finally, we only want to know bridge statistics of the bridges that
# were distributed via email.  Read assignments.csv.
cat("Reading assignments.csv\n")
assignments <- read.csv("assignments.csv", stringsAsFactors = FALSE)
cat("Read", length(assignments$fingerprint), "rows from",
    "assignments.csv.\n")

# Filter out all descriptors of bridges that were not assigned to the
# email distributor.
cat("Filtering out bridges that have not been distributed via email.\n")
data <- data[!data$fingerprint %in%
        assignments[assignments$type == 'email', "fingerprint"], ]
cat(length(data$fingerprint), "descriptors remain.  Again, Here are the",
    "first 10 rows, sorted by\nfingerprint and bridge stats interval",
    "end, and only displaying German\nand French users:\n")
data[1:10, c("fingerprint", "bridgestatsend", "de", "fr")]

# That's it.
cat("Terminating.\n")

