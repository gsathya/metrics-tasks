# Usage: Save consensus valid-after times to deviant-consensus-times.txt
#        with lines formatted as "YYYY-MM-DD-HH-MM-SS-consensus" and run
#        R --slave -f deviant-consensus-times.R
library(ggplot2)
c <- read.table("deviant-consensus-times.txt", header = FALSE,
  stringsAsFactors = FALSE)
c <- data.frame(
  month = as.Date(paste(substr(c$V1, 1, 7), "-01", sep = "")),
  datetime = as.POSIXct(paste("1971-03-", substr(c$V1, 9, 19), sep = ""),
  format = "%Y-%m-%d-%H-%M-%S"))
ggplot(c, aes(x = datetime, y = month)) +
geom_point(color = "red") +
scale_x_datetime(name = "\nDay of month and time", format = "%d") +
scale_y_date(name = "Month\n", format = "%Y-%m") +
opts(title = "Deviant consensuses\n")
ggsave("deviant-consensus-times.png", width = 8, height = 6, dpi = 72)

