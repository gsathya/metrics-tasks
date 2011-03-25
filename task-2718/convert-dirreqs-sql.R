library(ggplot2)
data <- read.csv("dirreqs-sql.csv", header = FALSE)
data <- data.frame(fingerprint = data$V1, statsend = data$V2,
  seconds = data$V3, country = data$V4, requests = data$V5)
data <- cast(data, fingerprint + statsend + seconds ~ country,
  value = "requests")
write.csv(data, file = "dirreqs.csv", quote = FALSE, row.names = FALSE)

