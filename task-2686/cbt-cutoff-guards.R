# Load ggplot library without printing out stupid warnings
options(warn = -1)
suppressPackageStartupMessages(library("ggplot2"))

# Read data
cbt <- read.csv("filtered.csv", stringsAsFactors = FALSE)

# Transform data to put it in an ECDF.
transform <- function(data, filenamepart, guards, cbtcutoff, filesize) {

  # Remove unfinished or timed out downloads
  data <- data[data$timeout == FALSE & data$failure == FALSE, ]

  # Remove all other cbt cutoffs and file sizes
  data <- data[data$guards == filenamepart & data$filesize == filesize, ]

  # Order data frame by completion time
  data <- data[order(data$completemillis), ]

  # Convert to a data frame that has ordered completion times on the x
  # axis and CDF value on the y axis, plus source and filesize for
  # distinguishing these values from the other data frames we're going to
  # bind together.
  data <- data.frame(
          x = data$completemillis,
          y = (1:length(data$completemillis)) / length(data$completemillis),
          guards = guards,
          cbtcutoff = cbtcutoff,
          filesize = filesize,
          stringsAsFactors = FALSE)

  # Return the transformed data frame
  data
}

data <- rbind(
  transform(cbt, "slow50cbt", "slow", "50", "50kb"),
  transform(cbt, "slow60cbt", "slow", "60", "50kb"),
  transform(cbt, "slow70cbt", "slow", "70", "50kb"),
  transform(cbt, "slow80cbt", "slow", "80", "50kb"),
  transform(cbt, "slow99cbt", "slow", "99", "50kb"),
  transform(cbt, "slow50cbt", "slow", "50", "1mb"),
  transform(cbt, "slow60cbt", "slow", "60", "1mb"),
  transform(cbt, "slow70cbt", "slow", "70", "1mb"),
  transform(cbt, "slow80cbt", "slow", "80", "1mb"),
  transform(cbt, "slow99cbt", "slow", "99", "1mb"),
  transform(cbt, "fast50cbt", "fast", "50", "50kb"),
  transform(cbt, "fast60cbt", "fast", "60", "50kb"),
  transform(cbt, "fast70cbt", "fast", "70", "50kb"),
  transform(cbt, "fast80cbt", "fast", "80", "50kb"),
  transform(cbt, "fast99cbt", "fast", "99", "50kb"),
  transform(cbt, "fast50cbt", "fast", "50", "1mb"),
  transform(cbt, "fast60cbt", "fast", "60", "1mb"),
  transform(cbt, "fast70cbt", "fast", "70", "1mb"),
  transform(cbt, "fast80cbt", "fast", "80", "1mb"),
  transform(cbt, "fast99cbt", "fast", "99", "1mb"),
  transform(cbt, "regular50cbt", "regular", "50", "50kb"),
  transform(cbt, "regular60cbt", "regular", "60", "50kb"),
  transform(cbt, "regular70cbt", "regular", "70", "50kb"),
  transform(cbt, "regular80cbt", "regular", "80", "50kb"),
  transform(cbt, "regular99cbt", "regular", "99", "50kb"),
  transform(cbt, "regular50cbt", "regular", "50", "1mb"),
  transform(cbt, "regular60cbt", "regular", "60", "1mb"),
  transform(cbt, "regular70cbt", "regular", "70", "1mb"),
  transform(cbt, "regular80cbt", "regular", "80", "1mb"),
  transform(cbt, "regular99cbt", "regular", "99", "1mb"))

data <- data[data$y <= 0.9975, ]
data <- data[(data$x <= 120000 & data$filesize == "50kb") |
             (data$x <= 600000 & data$filesize == "1mb"), ]

data[data$guards == "slow", "guards"] <- "a) slowest overall"
data[data$guards == "regular", "guards"] <- "c) default"
data[data$guards == "fast", "guards"] <- "e) fastest overall"
data[data$filesize == "50kb", "filesize"] <- "a) 50 KB"
data[data$filesize == "1mb", "filesize"] <- "b) 1 MB"

ggplot(data, aes(x = x / 1000, y = y, colour = cbtcutoff)) +
geom_line(size = 0.5) +
facet_grid(guards ~ filesize, scale = "free_x") +
scale_x_continuous("\nDownload completion time in seconds") +
scale_y_continuous("Fraction of completed downloads\n",
  limits = c(0.85, 1), breaks = seq(0.9, 1, 0.05), formatter = "percent") +
scale_colour_manual("CBT cutoff", breaks = c("50", "60", "70", "80", "99"),
  values = alpha("black", c(0.2, 0.35, 0.5, 0.7, 1))) +
opts(title = "Influence of guard nodes and circuit build timeouts on worst case performance\n")
ggsave("cbt-cutoff.png", width = 8, height = 6, dpi = 150)

