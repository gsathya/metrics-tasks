library(ggplot2)

# Read data
data <- read.csv("torperf-guard-bandwidths-ranks.csv",
  stringsAsFactors = FALSE)
data <- data[(data$filesize == "50kb" & data$completiontime < 60000) |
             (data$filesize == "1mb" & data$completiontime < 600000) |
             (data$filesize == "5mb" & data$completiontime < 1500000), ]
data[data$filesize == "50kb", "filesize"] <- "a) 50 KB"
data[data$filesize == "1mb", "filesize"] <- "b) 1 MB"
data[data$filesize == "5mb", "filesize"] <- "c) 5 MB"

percentiles <- paste("p", seq(90, 90, 1), sep = "")

rq <- read.csv("torperf-guard-rank-quantiles.csv",
  stringsAsFactors = FALSE)
rq[(rq$len < 30 & rq$filesize == "50kb") |
   (rq$len < 10 & rq$filesize == "1mb") |
   (rq$len < 5 & rq$filesize == "5mb"), percentiles] <- NA
rq <- rq[, c("filesize", "rank", percentiles)]
rq <- melt(rq, id = c("filesize", "rank"))
rq[rq$filesize == "50kb", "filesize"] <- "a) 50 KB"
rq[rq$filesize == "1mb", "filesize"] <- "b) 1 MB"
rq[rq$filesize == "5mb", "filesize"] <- "c) 5 MB"
ggplot(data, aes(x = as.numeric(rank), y = completiontime / 1000)) +
geom_point(alpha = 0.05) +
scale_x_continuous(paste("\nGuard rank by consensus bandwidth from",
  "slowest (0) to fastest (1)"), limits = c(0, 1)) +
scale_y_continuous("Torperf completion time in seconds\n") +
geom_line(data = rq, aes(x = as.numeric(rank), y = value / 1000,
  colour = variable)) +
facet_grid(filesize ~ ., scale = "free_y") +
opts(legend.position = "none")
ggsave(filename = "torperf-guard-rank-quantiles.png",
  width = 8, height = 5, dpi = 150)

bq <- read.csv("torperf-guard-bandwidth-quantiles.csv",
  stringsAsFactors = FALSE)
bq[(bq$len < 30 & bq$filesize == "50kb") |
   (bq$len < 10 & bq$filesize == "1mb") |
   (bq$len < 5 & bq$filesize == "5mb"), percentiles] <- NA
bq <- bq[, c("filesize", "bandwidth", percentiles)]
bq <- melt(bq, id = c("filesize", "bandwidth"))
bq[bq$filesize == "50kb", "filesize"] <- "a) 50 KB"
bq[bq$filesize == "1mb", "filesize"] <- "b) 1 MB"
bq[bq$filesize == "5mb", "filesize"] <- "c) 5 MB"
ggplot(data, aes(x = bandwidth / 1000, y = completiontime / 1000)) +
geom_point(alpha = 0.05) +
scale_x_continuous("\nGuard consensus bandwidth in MB/s") +
scale_y_continuous("Torperf completion time in seconds\n") +
geom_line(data = bq, aes(x = bandwidth / 1000, y = value / 1000,
  colour = variable)) +
facet_grid(filesize ~ ., scale = "free_y") +
opts(legend.position = "none")
ggsave(filename = "torperf-guard-bandwidth-quantiles.png",
  width = 8, height = 5, dpi = 150)

