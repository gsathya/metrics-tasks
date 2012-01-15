library(ggplot2)
d <- read.csv("out.csv", header = FALSE,
  col.names = c("dirauth", "intervalend", "writtendirreqbytes"))
d <- data.frame(dirauth = d$dirauth,
  date = as.Date(d$intervalend / 86400, origin = "1970-01-01 00:00:00"),
  writtendirreqbytes = d$writtendirreqbytes, count = 1)
c <- aggregate(x = list(count = d$count),
  by = list(dirauth = d$dirauth, date = d$date), FUN = sum)
d <- aggregate(x = list(writtendirreqbytes = d$writtendirreqbytes),
  by = list(dirauth = d$dirauth, date = d$date), FUN = sum)
e <- data.frame(dirauth = d$dirauth, date = d$date,
  writtendirreqbytespersec = (d$writtendirreqbytes / c$count) / 900)
ggplot(e, aes(x = date, y = writtendirreqbytespersec / 1024,
  colour = dirauth)) +
geom_line() +
scale_x_date(name = "\nDate") +
scale_y_continuous(name = "KiB/s\n") +
scale_colour_hue(name = "Directory authority") +
opts(title = "Written bytes on CONN_TYPE_DIR connections\n")
ggsave("dirauth-written-dirreq-bytes.png", width = 8, height = 6,
  dpi = 100)

