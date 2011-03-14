# Usage: R --slave -f verify.R

if (file.exists("descriptors.csv")) {
  cat("Verifying descriptors.csv. This may take a while.\n")
  d <- read.csv("descriptors.csv", stringsAsFactors = FALSE)
  cat(" ", length(na.omit(d$bridgestatsend)), "of", length(d$descriptor),
      "descriptors contain bridge stats.\n")
} else {
  cat("descriptors.csv does not exist\n")
}

if (file.exists("statuses.csv")) {
  cat("Verifying statuses.csv. This may take a while.\n")
  s <- read.csv("statuses.csv", stringsAsFactors = FALSE)
  cat(" ", length(s[s$running == TRUE, "running"]), "of",
      length(s$running), "bridges contained in the statuses have the",
      "Running flag.\n")
} else {
  cat("statuses.csv does not exist\n")
}

if (file.exists("relays.csv")) {
  cat("Verifying relays.csv. This may take a while.\n")
  r <- read.csv("relays.csv", stringsAsFactors = FALSE)
  summary(as.POSIXct(r$consensus))
}

if (file.exists("assignments.csv")) {
  cat("Verifying assignments.csv. This may take a while.\n")
  r <- read.csv("assignments.csv", stringsAsFactors = FALSE)
  summary(as.POSIXct(r$assignment))
}

