create index names_year on magnets(name_id,year);
create index names_episode on magnets(name_id,episode);
create index names_id on names(name,id);
create index ids_name on names(id);
create index ids_magnets on magnets(id);
create index year on magnets(year);
create index dn on magnets(dn);